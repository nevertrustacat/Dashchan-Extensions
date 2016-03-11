package com.mishiranu.dashchan.chan.local;

import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanLocator;

public class LocalChanLocator extends ChanLocator
{
	private static final Pattern THREAD_PATH = Pattern.compile("/null/res/([^/\\.]+)\\.html");
	
	public LocalChanLocator()
	{
		addChanHost("localhost");
	}
	
	@Override
	public boolean isBoardUri(Uri uri)
	{
		return false;
	}
	
	@Override
	public boolean isThreadUri(Uri uri)
	{
		return isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH);
	}
	
	@Override
	public boolean isAttachmentUri(Uri uri)
	{
		return false;
	}
	
	@Override
	public String getBoardName(Uri uri)
	{
		return null;
	}
	
	@Override
	public String getThreadNumber(Uri uri)
	{
		return getGroupValue(uri.getPath(), THREAD_PATH, 1);
	}
	
	@Override
	public String getPostNumber(Uri uri)
	{
		return uri.getFragment();
	}
	
	@Override
	public Uri createBoardUri(String boardName, int pageNumber)
	{
		return pageNumber > 0 ? buildPath("null", pageNumber + ".html") : buildPath(boardName, "");
	}
	
	@Override
	public Uri createThreadUri(String boardName, String threadNumber)
	{
		return buildPath("null", "res", threadNumber + ".html");
	}
	
	@Override
	public Uri createPostUri(String boardName, String threadNumber, String postNumber)
	{
		return createThreadUri(boardName, threadNumber).buildUpon().fragment(postNumber).build();
	}
}