package com.mishiranu.dashchan.chan.infinite;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Base64;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class InfiniteChanPerformer extends ChanPerformer
{
	private static final String[] BOARDS_GENERAL = {"b", "boards", "meta", "n", "operate"};

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		InfiniteChanLocator locator = InfiniteChanLocator.get(this);
		if (data.isCatalog())
		{
			Uri uri = locator.buildPath(data.boardName, "catalog.json");
			JSONArray jsonArray = new HttpRequest(uri, data.holder, data).read().getJsonArray();
			if (jsonArray == null) throw new InvalidResponseException();
			if (jsonArray.length() == 1)
			{
				try
				{
					JSONObject jsonObject = jsonArray.getJSONObject(0);
					if (!jsonObject.has("threads")) return null;
				}
				catch (JSONException e)
				{
					throw new InvalidResponseException(e);
				}
			}
			try
			{
				ArrayList<Posts> threads = new ArrayList<>();
				for (int i = 0; i < jsonArray.length(); i++)
				{
					JSONArray threadsArray = jsonArray.getJSONObject(i).getJSONArray("threads");
					for (int j = 0; j < threadsArray.length(); j++)
					{
						threads.add(InfiniteModelMapper.createThread(threadsArray.getJSONObject(j),
								locator, data.boardName, true));
					}
				}
				return new ReadThreadsResult(threads);
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		else
		{
			Uri uri = locator.buildPath(data.boardName, data.pageNumber + ".json");
			JSONObject jsonObject = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
					.read().getJsonObject();
			if (jsonObject == null) throw new InvalidResponseException();
			if (data.pageNumber == 0)
			{
				uri = locator.buildQuery("settings.php", "board", data.boardName);
				JSONObject boardConfigObject = new HttpRequest(uri, data.holder, data).read().getJsonObject();
				if (boardConfigObject != null)
				{
					InfiniteChanConfiguration configuration = InfiniteChanConfiguration.get(this);
					configuration.updateFromBoardJson(data.boardName, boardConfigObject, true);
				}
			}
			try
			{
				JSONArray threadsArray = jsonObject.getJSONArray("threads");
				Posts[] threads = new Posts[threadsArray.length()];
				for (int i = 0; i < threads.length; i++)
				{
					threads[i] = InfiniteModelMapper.createThread(threadsArray.getJSONObject(i),
							locator, data.boardName, false);
				}
				return new ReadThreadsResult(threads);
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException
	{
		InfiniteChanLocator locator = InfiniteChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "res", data.threadNumber + ".json");
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
				.read().getJsonObject();
		if (jsonObject != null)
		{
			try
			{
				JSONArray jsonArray = jsonObject.getJSONArray("posts");
				if (jsonArray.length() > 0)
				{
					Post[] posts = new Post[jsonArray.length()];
					for (int i = 0; i < posts.length; i++)
					{
						posts[i] = InfiniteModelMapper.createPost(jsonArray.getJSONObject(i),
								locator, data.boardName);
					}
					return new ReadPostsResult(posts);
				}
				return null;
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}

	@Override
	public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
			InvalidResponseException
	{
		InfiniteChanLocator locator = InfiniteChanLocator.get(this);
		Uri uri = locator.buildQuery("search.php", "board", data.boardName, "search", data.searchQuery);
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		try
		{
			return new ReadSearchPostsResult(new InfiniteSearchParser(responseText, this).convertPosts());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException
	{
		InfiniteChanLocator locator = InfiniteChanLocator.get(this);
		InfiniteChanConfiguration configuration = InfiniteChanConfiguration.get(this);
		Board[] boards = new Board[BOARDS_GENERAL.length];
		try
		{
			for (int i = 0; i < BOARDS_GENERAL.length; i++)
			{
				Uri uri = locator.buildQuery("settings.php", "board", BOARDS_GENERAL[i]);
				JSONObject jsonObject = new HttpRequest(uri, data.holder, data).read().getJsonObject();
				if (jsonObject == null) throw new InvalidResponseException();
				String title = CommonUtils.getJsonString(jsonObject, "title");
				String description = CommonUtils.optJsonString(jsonObject, "subtitle");
				configuration.updateFromBoardJson(BOARDS_GENERAL[i], jsonObject, false);
				boards[i] = new Board(BOARDS_GENERAL[i], title, description);
			}
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
		return new ReadBoardsResult(new BoardCategory("General", boards));
	}

	@Override
	public ReadUserBoardsResult onReadUserBoards(ReadUserBoardsData data) throws HttpException, InvalidResponseException
	{
		InfiniteChanLocator locator = InfiniteChanLocator.get(this);
		Uri uri = locator.buildPath("boards.json");
		JSONArray jsonArray = new HttpRequest(uri, data.holder, data).read().getJsonArray();
		if (jsonArray != null)
		{
			try
			{
				HashSet<String> general = new HashSet<>();
				Collections.addAll(general, BOARDS_GENERAL);
				ArrayList<Board> boards = new ArrayList<>(jsonArray.length());
				for (int i = 0; i < jsonArray.length(); i++)
				{
					JSONObject jsonObject = jsonArray.getJSONObject(i);
					String boardName = CommonUtils.getJsonString(jsonObject, "uri");
					if (!general.contains(boardName))
					{
						String title = CommonUtils.getJsonString(jsonObject, "title");
						String description = CommonUtils.optJsonString(jsonObject, "subtitle");
						boards.add(new Board(boardName, title, description));
					}
				}
				return new ReadUserBoardsResult(boards);
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException, InvalidResponseException
	{
		InfiniteChanLocator locator = InfiniteChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "res", data.threadNumber + ".json");
		JSONObject jsonObject = new HttpRequest(uri, data.holder, data).setValidator(data.validator)
				.read().getJsonObject();
		if (jsonObject != null)
		{
			try
			{
				return new ReadPostsCountResult(jsonObject.getJSONArray("posts").length());
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}

	private static final Pattern PATTERN_CAPTCHA = Pattern.compile("<image src=\"data:image/png;base64,(.*?)\">" +
			"(?:.*?value=['\"]([^'\"]+?)['\"])?");

	private static final String COOKIE_TOR = "tor";

	private static final String REQUIREMENT_REPORT = "report";
	private static final String REQUIREMENT_DNSBLS = "dnsbls";

	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException
	{
		InfiniteChanLocator locator = InfiniteChanLocator.get(this);
		String challenge = null;
		Bitmap image = null;
		if (data.requirement != null && data.requirement.startsWith(REQUIREMENT_REPORT))
		{
			String postNumber = data.requirement.substring(REQUIREMENT_REPORT.length());
			Uri uri = locator.buildQuery("report.php", "board", data.boardName, "post", "delete_" + postNumber);
			String responseText = new HttpRequest(uri, data.holder, data).read().getString();
			Matcher matcher = PATTERN_CAPTCHA.matcher(responseText);
			if (matcher.find())
			{
				String base64 = matcher.group(1);
				challenge = matcher.group(2);
				byte[] imageArray = Base64.decode(base64, Base64.DEFAULT);
				image = BitmapFactory.decodeByteArray(imageArray, 0, imageArray.length);
			}
			if (image == null) throw new InvalidResponseException();
		}
		else if (REQUIREMENT_DNSBLS.equals(data.requirement))
		{
			String responseText = new HttpRequest(locator.buildPath("dnsbls_bypass.php"), data.holder, data)
					.read().getString();
			Matcher matcher = PATTERN_CAPTCHA.matcher(responseText);
			if (matcher.find())
			{
				String base64 = matcher.group(1);
				challenge = matcher.group(2);
				byte[] imageArray = Base64.decode(base64, Base64.DEFAULT);
				image = BitmapFactory.decodeByteArray(imageArray, 0, imageArray.length);
			}
			if (image == null) throw new InvalidResponseException();
		}
		else
		{
			Uri uri = locator.buildQuery("settings.php", "board", data.boardName);
			JSONObject jsonObject = new HttpRequest(uri, data.holder, data).read().getJsonObject();
			if (jsonObject == null) throw new InvalidResponseException();
			try
			{
				boolean newThreadCaptcha = jsonObject.optBoolean("new_thread_capt");
				jsonObject = jsonObject.getJSONObject("captcha");
				if (jsonObject.getBoolean("enabled") || data.threadNumber == null && newThreadCaptcha)
				{
					String extra = CommonUtils.getJsonString(jsonObject, "extra");
					Uri providerUri = Uri.parse(CommonUtils.getJsonString(jsonObject, "provider_get"));
					uri = providerUri.buildUpon().scheme(uri.getScheme()).authority(uri.getAuthority())
							.appendQueryParameter("mode", "get").appendQueryParameter("extra", extra).build();
					jsonObject = new HttpRequest(uri, data.holder, data).read().getJsonObject();
					Matcher matcher = PATTERN_CAPTCHA.matcher(CommonUtils.getJsonString(jsonObject, "captchahtml"));
					if (matcher.matches())
					{
						String base64 = matcher.group(1);
						byte[] imageArray = Base64.decode(base64, Base64.DEFAULT);
						image = BitmapFactory.decodeByteArray(imageArray, 0, imageArray.length);
						challenge = CommonUtils.getJsonString(jsonObject, "cookie");
					}
					if (image == null) throw new InvalidResponseException();
				}
			}
			catch (JSONException e)
			{
				throw new InvalidResponseException(e);
			}
		}
		if (challenge == null) return new ReadCaptchaResult(CaptchaState.SKIP, null);
		Bitmap newImage = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
		Paint paint = new Paint();
		float[] colorMatrixArray = {0.3f, 0.3f, 0.3f, 0f, 48f, 0.3f, 0.3f, 0.3f, 0f, 48f,
				0.3f, 0.3f, 0.3f, 0f, 48f, 0f, 0f, 0f, 1f, 0f};
		paint.setColorFilter(new ColorMatrixColorFilter(colorMatrixArray));
		new Canvas(newImage).drawBitmap(image, 0f, 0f, paint);
		image.recycle();
		CaptchaData captchaData = new CaptchaData();
		captchaData.put(CaptchaData.CHALLENGE, challenge);
		return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(newImage);
	}

	private boolean checkDnsBlsCaptcha(HttpHolder holder) throws HttpException
	{
		InfiniteChanLocator locator = InfiniteChanLocator.get(this);
		Uri uri = locator.buildPath("dnsbls_bypass.php");
		boolean retry = false;
		while (true)
		{
			CaptchaData captchaData = requireUserCaptcha(REQUIREMENT_DNSBLS, null, null, retry);
			if (captchaData == null) return false;
			retry = true;
			String responseText = new HttpRequest(uri, holder).setPostMethod(new UrlEncodedEntity("captcha_cookie",
					captchaData.get(CaptchaData.CHALLENGE), "captcha_text", captchaData.get(CaptchaData.INPUT)))
					.setSuccessOnly(false).read().getString();
			if (holder.getResponseCode() != HttpURLConnection.HTTP_BAD_REQUEST) holder.checkResponseCode();
			if (responseText == null || !responseText.contains("<h1>Success!</h1>")) continue;
			String torCookie = holder.getCookieValue("tor");
			if (torCookie != null)
			{
				InfiniteChanConfiguration configuration = InfiniteChanConfiguration.get(this);
				configuration.storeCookie(COOKIE_TOR, torCookie, "Tor");
			}
			return true;
		}
	}

	private static final Pattern PATTERN_ERROR = Pattern.compile("<(?:strong|h2).*?>(.*?)</(?:strong|h2)>");

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException
	{
		MultipartEntity entity = new MultipartEntity();
		entity.add("post", "on");
		entity.add("board", data.boardName);
		entity.add("thread", data.threadNumber);
		entity.add("subject", data.subject);
		entity.add("body", StringUtils.emptyIfNull(data.comment));
		entity.add("name", data.name);
		entity.add("email", data.email);
		entity.add("password", data.password);
		if (data.optionSage) entity.add("no-bump", "on");
		entity.add("user_flag", data.userIcon);
		boolean hasSpoilers = false;
		if (data.attachments != null)
		{
			for (int i = 0; i < data.attachments.length; i++)
			{
				SendPostData.Attachment attachment = data.attachments[i];
				attachment.addToEntity(entity, "file" + (i > 0 ? i + 1 : ""));
				hasSpoilers |= attachment.optionSpoiler;
			}
		}
		if (hasSpoilers) entity.add("spoiler", "on");
		if (data.captchaData != null && data.captchaData.get(CaptchaData.CHALLENGE) != null)
		{
			entity.add("captcha_cookie", StringUtils.emptyIfNull(data.captchaData.get(CaptchaData.CHALLENGE)));
			entity.add("captcha_text", StringUtils.emptyIfNull(data.captchaData.get(CaptchaData.INPUT)));
		}
		entity.add("json_response", "1");

		InfiniteChanConfiguration configuration = InfiniteChanConfiguration.get(this);
		String torCookie = configuration.getCookie(COOKIE_TOR);
		InfiniteChanLocator locator = InfiniteChanLocator.get(this);
		Uri uri = locator.buildPath("post.php");
		String responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.addCookie(COOKIE_TOR, torCookie).addHeader("Referer", locator.buildPath().toString())
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getString();
		String errorMessage;
		try
		{
			JSONObject jsonObject = new JSONObject(responseText);
			String redirect = jsonObject.optString("redirect");
			if (!StringUtils.isEmpty(redirect))
			{
				uri = locator.buildPath(redirect);
				String threadNumber = locator.getThreadNumber(uri);
				String postNumber = locator.getPostNumber(uri);
				return new SendPostResult(threadNumber, postNumber);
			}
			errorMessage = jsonObject.optString("error");
		}
		catch (JSONException e)
		{
			Matcher matcher = PATTERN_ERROR.matcher(responseText);
			if (!matcher.find()) throw new InvalidResponseException();
			errorMessage = matcher.group(1);
		}

		if (errorMessage != null)
		{
			int errorType = 0;
			if (errorMessage.contains("dnsbls_bypass"))
			{
				boolean success = checkDnsBlsCaptcha(data.holder);
				if (success) return onSendPost(data);
				else errorType = ApiException.SEND_ERROR_NO_ACCESS;
			}
			else if (errorMessage.contains("CAPTCHA expired"))
			{
				errorType = ApiException.SEND_ERROR_CAPTCHA;
			}
			else if (errorMessage.contains("The body was") || errorMessage.contains("must be at least"))
			{
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			}
			else if (errorMessage.contains("You must upload an image"))
			{
				errorType = ApiException.SEND_ERROR_EMPTY_FILE;
			}
			else if (errorMessage.contains("The file was too big") || errorMessage.contains("is longer than"))
			{
				errorType = ApiException.SEND_ERROR_FILE_TOO_BIG;
			}
			else if (errorMessage.contains("You have attempted to upload too many"))
			{
				errorType = ApiException.SEND_ERROR_FILES_TOO_MANY;
			}
			else if (errorMessage.contains("was too long"))
			{
				errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG;
			}
			else if (errorMessage.contains("Thread locked"))
			{
				errorType = ApiException.SEND_ERROR_CLOSED;
			}
			else if (errorMessage.contains("Invalid board"))
			{
				errorType = ApiException.SEND_ERROR_NO_BOARD;
			}
			else if (errorMessage.contains("Thread specified does not exist"))
			{
				errorType = ApiException.SEND_ERROR_NO_THREAD;
			}
			else if (errorMessage.contains("Unsupported image format"))
			{
				errorType = ApiException.SEND_ERROR_FILE_NOT_SUPPORTED;
			}
			else if (errorMessage.contains("That file"))
			{
				errorType = ApiException.SEND_ERROR_FILE_EXISTS;
			}
			else if (errorMessage.contains("Flood detected"))
			{
				errorType = ApiException.SEND_ERROR_TOO_FAST;
			}
			if (errorType != 0) throw new ApiException(errorType);
			CommonUtils.writeLog("8chan send message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		InfiniteChanLocator locator = InfiniteChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("delete", "on", "board", data.boardName,
				"password", data.password, "json_response", "1");
		for (String postNumber : data.postNumbers) entity.add("delete_" + postNumber, "on");
		if (data.optionFilesOnly) entity.add("file", "on");
		Uri uri = locator.buildPath("post.php");
		String responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).setSuccessOnly(false)
				.read().getString();
		String errorMessage;
		try
		{
			JSONObject jsonObject = new JSONObject(responseText);
			if (jsonObject.optBoolean("success")) return null;
			errorMessage = jsonObject.optString("error");
		}
		catch (JSONException e)
		{
			Matcher matcher = PATTERN_ERROR.matcher(responseText);
			if (!matcher.find()) throw new InvalidResponseException();
			errorMessage = matcher.group(1);
		}
		if (errorMessage != null)
		{
			int errorType = 0;
			if (errorMessage.contains("dnsbls_bypass"))
			{
				boolean success = checkDnsBlsCaptcha(data.holder);
				if (success) return onSendDeletePosts(data);
				else errorType = ApiException.DELETE_ERROR_NO_ACCESS;
			}
			else if (errorMessage.contains("Wrong password"))
			{
				errorType = ApiException.DELETE_ERROR_PASSWORD;
			}
			else if (errorMessage.contains("before deleting that"))
			{
				errorType = ApiException.DELETE_ERROR_TOO_NEW;
			}
			else if (errorMessage.contains("That post has no files"))
			{
				return null;
			}
			if (errorType != 0) throw new ApiException(errorType);
			CommonUtils.writeLog("8chan delete message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		String postNumber = data.postNumbers.get(0);
		boolean retry = false;
		while (true)
		{
			CaptchaData captchaData = requireUserCaptcha(REQUIREMENT_REPORT + postNumber,
					data.boardName, data.threadNumber, retry);
			if (captchaData == null) throw new ApiException(ApiException.REPORT_ERROR_NO_ACCESS);
			retry = true;
			if (Thread.currentThread().isInterrupted()) return null;
			InfiniteChanLocator locator = InfiniteChanLocator.get(this);
			UrlEncodedEntity entity = new UrlEncodedEntity("report", "1", "board", data.boardName);
			entity.add("delete_" + postNumber, "1");
			entity.add("reason", StringUtils.emptyIfNull(data.comment));
			if (data.options.contains("global")) entity.add("global", "1");
			entity.add("captcha_cookie", StringUtils.emptyIfNull(captchaData.get(CaptchaData.CHALLENGE)));
			entity.add("captcha_text", StringUtils.emptyIfNull(captchaData.get(CaptchaData.INPUT)));
			entity.add("json_response", "1");
			Uri uri = locator.buildPath("post.php");
			String responseText = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
					.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).setSuccessOnly(false)
					.read().getString();
			String errorMessage;
			try
			{
				JSONObject jsonObject = new JSONObject(responseText);
				if (jsonObject.optBoolean("success")) return null;
				errorMessage = jsonObject.optString("error");
			}
			catch (JSONException e)
			{
				Matcher matcher = PATTERN_ERROR.matcher(responseText);
				if (!matcher.find()) throw new InvalidResponseException();
				errorMessage = matcher.group(1);
			}
			if (errorMessage != null)
			{
				int errorType = 0;
				if (errorMessage.contains("dnsbls_bypass"))
				{
					boolean success = checkDnsBlsCaptcha(data.holder);
					if (success) return onSendReportPosts(data);
					else errorType = ApiException.REPORT_ERROR_NO_ACCESS;
				}
				else if (errorMessage.contains("CAPTCHA expired"))
				{
					continue;
				}
				if (errorType != 0) throw new ApiException(errorType);
				CommonUtils.writeLog("8chan report message", errorMessage);
				throw new ApiException(errorMessage);
			}
			throw new InvalidResponseException();
		}
	}
}