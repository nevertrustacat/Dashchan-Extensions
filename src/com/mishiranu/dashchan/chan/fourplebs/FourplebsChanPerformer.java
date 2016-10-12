package com.mishiranu.dashchan.chan.fourplebs;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.util.Pair;

import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.ThreadRedirectException;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.text.ParseException;
import chan.util.CommonUtils;

public class FourplebsChanPerformer extends ChanPerformer
{
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		FourplebsChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "page", Integer.toString(data.pageNumber + 1), "");
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		try
		{
			return new ReadThreadsResult(new FourplebsPostsParser(responseText, this).convertThreads());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}

	private static final Pattern PATTERN_REDIRECT = Pattern.compile("You are being redirected to .*?/thread/(\\d+)/#");

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, ThreadRedirectException,
			InvalidResponseException
	{
		FourplebsChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
				.setSuccessOnly(false).read().getString();
		if (data.holder.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
		{
			uri = locator.buildPath(data.boardName, "post", data.threadNumber, "");
			responseText = new HttpRequest(uri, data.holder, data).read().getString();
			Matcher matcher = PATTERN_REDIRECT.matcher(responseText);
			if (matcher.find()) throw new ThreadRedirectException(data.boardName, matcher.group(1), data.threadNumber);
			throw HttpException.createNotFoundException();
		}
		else data.holder.checkResponseCode();
		try
		{
			Uri threadUri = locator.buildPathWithHost("boards.4chan.org", data.boardName, "thread", data.threadNumber);
			return new ReadPostsResult(new FourplebsPostsParser(responseText, this).convertPosts(threadUri));
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
			InvalidResponseException
	{
		FourplebsChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "search", "text").buildUpon().appendPath(data.searchQuery)
				.appendEncodedPath("page/" + (data.pageNumber + 1) + "/").build();
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try
		{
			return new ReadSearchPostsResult(new FourplebsPostsParser(responseText, this).convertSearch());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		FourplebsChanLocator locator = ChanLocator.get(this);
		String responseText = new HttpRequest(locator.buildPath(), data.holder, data).read().getString();
		try
		{
			return new ReadBoardsResult(new FourplebsBoardsParser(responseText).convert());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}

	private static final Pattern PATTERN_FLAG_DATA_CSS = Pattern.compile("(flag-[a-z]+)" +
			"\\{ *background-position: *(-?\\d+)(?:px)? +(-?\\d+)(?:px)?;? *\\}");

	private final HashMap<String, Pair<Integer, Integer>> mFlagDatas = new HashMap<>();

	@Override
	public ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException
	{
		FourplebsChanLocator locator = ChanLocator.get(this);
		String cssClass = locator.extractFlagStubClass(data.uri);
		if (cssClass != null)
		{
			String prefix = "foolfuuka/foolz/foolfuuka-theme-fourpleb/assets-0.1.6";
			Pair<Integer, Integer> flagData;
			synchronized (mFlagDatas)
			{
				if (mFlagDatas.isEmpty())
				{
					Uri uri = locator.buildPath(prefix, "flags.css");
					String responseText = new HttpRequest(uri, data.holder).read().getString();
					Matcher matcher = PATTERN_FLAG_DATA_CSS.matcher(responseText);
					while (matcher.find())
					{
						mFlagDatas.put("flag " + matcher.group(1), new Pair<>(Integer.parseInt(matcher.group(2)),
								Integer.parseInt(matcher.group(3))));
					}
					uri = locator.buildPath(prefix, "polflags.css");
					responseText = new HttpRequest(uri, data.holder).read().getString();
					matcher = PATTERN_FLAG_DATA_CSS.matcher(responseText);
					while (matcher.find())
					{
						mFlagDatas.put("flag-pol " + matcher.group(1), new Pair<>(Integer.parseInt(matcher.group(2)),
								Integer.parseInt(matcher.group(3))));
					}
				}
				flagData = mFlagDatas.get(cssClass);
			}
			if (flagData != null)
			{
				String path = null;
				switch (cssClass.split(" +")[0])
				{
					case "flag": path = prefix + "/images/flags.png"; break;
					case "flag-pol": path = prefix + "/images/polflags.png"; break;
				}
				if (path != null)
				{
					Uri uri = locator.buildPath(path);
					Bitmap bitmap = new HttpRequest(uri, data).read().getBitmap();
					if (bitmap == null) throw new InvalidResponseException();
					Bitmap image = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888);
					new Canvas(image).drawBitmap(bitmap, flagData.first, flagData.second, null);
					bitmap.recycle();
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					image.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
					image.recycle();
					return new ReadContentResult(new HttpResponse(outputStream.toByteArray()));
				}
			}
		}
		return super.onReadContent(data);
	}
}