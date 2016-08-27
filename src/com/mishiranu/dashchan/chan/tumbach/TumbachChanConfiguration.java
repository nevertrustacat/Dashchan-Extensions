package com.mishiranu.dashchan.chan.tumbach;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Pair;

import chan.content.ChanConfiguration;
import chan.util.CommonUtils;

public class TumbachChanConfiguration extends ChanConfiguration
{
	public static final String CAPTCHA_TYPE_NODE_CAPTCHA = "node_captcha";
	
	private static final String KEY_ATTACHMENTS_COUNT = "attachments_count";
	private static final String KEY_MAX_COMMENT_LENGTH = "max_comment_length";
	
	public TumbachChanConfiguration()
	{
		request(OPTION_READ_THREAD_PARTIALLY);
		request(OPTION_READ_SINGLE_POST);
		request(OPTION_READ_POSTS_COUNT);
		setDefaultName("Аноним");
		setBumpLimit(500);
		addCaptchaType(CAPTCHA_TYPE_NODE_CAPTCHA);
		addCaptchaType(CAPTCHA_TYPE_RECAPTCHA_2);
		addCaptchaType(CAPTCHA_TYPE_RECAPTCHA_1);
	}
	
	@Override
	public Board obtainBoardConfiguration(String boardName)
	{
		Board board = new Board();
		board.allowSearch = true;
		board.allowCatalog = true;
		board.allowPosting = true;
		board.allowDeleting = true;
		return board;
	}
	
	@Override
	public Captcha obtainCustomCaptchaConfiguration(String captchaType)
	{
		if (CAPTCHA_TYPE_NODE_CAPTCHA.equals(captchaType))
		{
			Captcha captcha = new Captcha();
			captcha.title = "Node Captcha";
			captcha.input = Captcha.Input.NUMERIC;
			captcha.validity = Captcha.Validity.IN_BOARD;
			return captcha;
		}
		return null;
	}
	
	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread)
	{
		Posting posting = new Posting();
		posting.allowName = true;
		posting.allowEmail = true;
		posting.allowSubject = true;
		posting.optionSage = true;
		posting.optionOriginalPoster = true;
		posting.maxCommentLength = get(boardName, KEY_MAX_COMMENT_LENGTH, 15000);
		posting.attachmentCount = get(boardName, KEY_ATTACHMENTS_COUNT, 1);
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("audio/*");
		posting.attachmentMimeTypes.add("video/webm");
		posting.attachmentMimeTypes.add("application/pdf");
		posting.attachmentRatings.add(new Pair<>("SFW", "SFW"));
		posting.attachmentRatings.add(new Pair<>("R-15", "R-15"));
		posting.attachmentRatings.add(new Pair<>("R-18", "R-18"));
		posting.attachmentRatings.add(new Pair<>("R-18G", "R-18G"));
		return posting;
	}
	
	@Override
	public Deleting obtainDeletingConfiguration(String boardName)
	{
		Deleting deleting = new Deleting();
		deleting.password = true;
		deleting.optionFilesOnly = true;
		return deleting;
	}
	
	public void updateFromBoardsJson(JSONObject jsonObject)
	{
		try
		{
			JSONArray jsonArray = jsonObject.getJSONArray("boards");
			for (int i = 0; i < jsonArray.length(); i++)
			{
				jsonObject = jsonArray.getJSONObject(i);
				String boardName = CommonUtils.getJsonString(jsonObject, "name");
				String defaultName = CommonUtils.getJsonString(jsonObject, "defaultUserName");
				int attachmentsCount = jsonObject.optInt("maxFileCount");
				int bumpLimit = jsonObject.optInt("bumpLimit");
				int maxCommentLength = jsonObject.optInt("maxTextLength");
				storeDefaultName(boardName, defaultName);
				set(boardName, KEY_ATTACHMENTS_COUNT, attachmentsCount);
				if (bumpLimit != 0) storeBumpLimit(boardName, bumpLimit);
				if (maxCommentLength > 0) set(boardName, KEY_MAX_COMMENT_LENGTH, maxCommentLength);
			}
		}
		catch (JSONException e)
		{
			
		}
	}
}