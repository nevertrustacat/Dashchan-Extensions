package com.mishiranu.dashchan.chan.local;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpResponse;
import chan.text.ParseException;

public class LocalChanPerformer extends ChanPerformer
{
	private static final int THREADS_PER_PAGE = 20;
	
	private static final Comparator<File> LAST_MODIFIED_COMPARATOR = new Comparator<File>()
	{
		@Override
		public int compare(File lhs, File rhs)
		{
			return ((Long) rhs.lastModified()).compareTo(lhs.lastModified());
		}
	};
	
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException
	{
		LocalChanConfiguration configuration = ChanConfiguration.get(this);
		Thread thread = Thread.currentThread();
		ArrayList<Posts> threads = new ArrayList<>();
		byte[] buffer = new byte[4096];
		ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
		int from = THREADS_PER_PAGE * data.pageNumber;
		int to = from + THREADS_PER_PAGE;
		int current = 0;
		File localDownloadDirectory = configuration.getLocalDownloadDirectory();
		File[] files = localDownloadDirectory.listFiles();
		if (files != null)
		{
			Arrays.sort(files, LAST_MODIFIED_COMPARATOR);
			for (File file : files)
			{
				String name = file.getName();
				if (file.isFile() && name.endsWith(".html"))
				{
					if (current >= from && current < to)
					{
						String threadNumber = name.substring(0, name.length() - 5);
						byte[] fileData = readFile(file, output, buffer);
						if (fileData != null && fileData.length > 0)
						{
							try
							{
								threads.add(new LocalPostsParser(new String(fileData), this, threadNumber,
										localDownloadDirectory).convertThread());
							}
							catch (ParseException e)
							{
								
							}
						}
						if (thread.isInterrupted()) return null;
					}
					current++;
				}
			}
		}
		if (threads.size() == 0)
		{
			if (data.pageNumber == 0) return null; else throw HttpException.createNotFoundException();
		}
		return new ReadThreadsResult(threads);
	}
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException
	{
		if (data.cachedPosts != null) return new ReadPostsResult(data.cachedPosts); // Do not allow models merging
		LocalChanConfiguration configuration = ChanConfiguration.get(this);
		File localDownloadDirectory = configuration.getLocalDownloadDirectory();
		File file = new File(localDownloadDirectory, data.threadNumber + ".html");
		if (file.exists())
		{
			byte[] fileData = readFile(file, null, null);
			if (fileData != null && fileData.length > 0)
			{
				try
				{
					return new ReadPostsResult(new LocalPostsParser(new String(fileData), this, data.threadNumber,
							localDownloadDirectory).convertPosts());
				}
				catch (ParseException e)
				{
					
				}
			}
			throw new InvalidResponseException();
		}
		throw HttpException.createNotFoundException();
	}
	
	@Override
	public ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException
	{
		if ("localhost".equals(data.uri.getAuthority()))
		{
			File file = new File(data.uri.getPath());
			if (file.exists()) return new ReadContentResult(new HttpResponse(readFile(file, null, null)));
			else throw HttpException.createNotFoundException();
		}
		return super.onReadContent(data);
	}
	
	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		boolean deleteThread = false;
		for (String postNumber : data.postNumbers)
		{
			if (data.threadNumber.endsWith("-" + postNumber))
			{
				deleteThread = true;
			}
		}
		if (!deleteThread) throw new ApiException(ApiException.DELETE_ERROR_NO_ACCESS);
		LocalChanConfiguration configuration = ChanConfiguration.get(this);
		File file = configuration.getLocalDownloadDirectory();
		Thread thread = Thread.currentThread();
		removeDirectory(thread, new File(file, data.threadNumber));
		if (!thread.isInterrupted()) new File(file, data.threadNumber + ".html").delete();
		return null;
	}
	
	private void removeDirectory(Thread thread, File directory)
	{
		File[] files = directory.listFiles();
		if (files != null)
		{
			for (File file : files)
			{
				if (thread.isInterrupted()) return;
				if (file.isDirectory()) removeDirectory(thread, file);
				else file.delete();
			}
		}
		directory.delete();
	}
	
	private byte[] readFile(File file, ByteArrayOutputStream output, byte[] buffer)
	{
		if (output != null) output.reset(); else output = new ByteArrayOutputStream();
		if (buffer == null) buffer = new byte[4096];
		FileInputStream input = null;
		try
		{
			input = new FileInputStream(file);
			int count;
			while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
			return output.toByteArray();
		}
		catch (IOException e)
		{
			return null;
		}
		finally
		{
			closeStream(input);
		}
	}
	
	private void closeStream(Closeable closeable)
	{
		if (closeable != null)
		{
			try
			{
				closeable.close();
			}
			catch (IOException e)
			{
				
			}
		}
	}
}