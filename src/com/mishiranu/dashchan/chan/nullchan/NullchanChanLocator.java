package com.mishiranu.dashchan.chan.nullchan;

import java.util.List;
import java.util.regex.Pattern;

import android.net.Uri;

import chan.content.ChanLocator;

public class NullchanChanLocator extends ChanLocator {
	private static final Pattern BOARD_PATH = Pattern.compile("/\\w+/?");
	private static final Pattern THREAD_PATH = Pattern.compile("/\\w+/(\\d+)/?");
	private static final Pattern ATTACHMENT_PATH = Pattern.compile("/\\d+/\\d+/\\d+/[^/]+");

	public NullchanChanLocator() {
		addChanHost("0chan.hk");
		addConvertableChanHost("www.0chan.hk");
		addSpecialChanHost("s01.0chan.hk");
		addSpecialChanHost("s02.0chan.hk");
		setHttpsMode(HttpsMode.CONFIGURABLE);
	}

	@Override
	public boolean isBoardUri(Uri uri) {
		return isChanHostOrRelative(uri) && isPathMatches(uri, BOARD_PATH);
	}

	@Override
	public boolean isThreadUri(Uri uri) {
		return isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH);
	}

	@Override
	public boolean isAttachmentUri(Uri uri) {
		return isChanHostOrRelative(uri) && isPathMatches(uri, ATTACHMENT_PATH);
	}

	@Override
	public String getBoardName(Uri uri) {
		List<String> segments = uri.getPathSegments();
		if (segments.size() > 0) {
			return segments.get(0);
		}
		return null;
	}

	@Override
	public String getThreadNumber(Uri uri) {
		return getGroupValue(uri.getPath(), THREAD_PATH, 1);
	}

	@Override
	public String getPostNumber(Uri uri) {
		return uri.getFragment();
	}

	@Override
	public Uri createBoardUri(String boardName, int pageNumber) {
		return buildPath(boardName);
	}

	@Override
	public Uri createThreadUri(String boardName, String threadNumber) {
		return buildPath(boardName, threadNumber);
	}

	@Override
	public Uri createPostUri(String boardName, String threadNumber, String postNumber) {
		return createThreadUri(boardName, threadNumber).buildUpon().fragment(postNumber).build();
	}
}