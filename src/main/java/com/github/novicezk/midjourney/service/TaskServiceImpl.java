package com.github.novicezk.midjourney.service;

import com.github.novicezk.midjourney.Constants;
import com.github.novicezk.midjourney.ReturnCode;
import com.github.novicezk.midjourney.enums.BlendDimensions;
import com.github.novicezk.midjourney.enums.TaskAction;
import com.github.novicezk.midjourney.loadbalancer.DiscordInstance;
import com.github.novicezk.midjourney.loadbalancer.DiscordLoadBalancer;
import com.github.novicezk.midjourney.result.Message;
import com.github.novicezk.midjourney.result.SubmitResultVO;
import com.github.novicezk.midjourney.support.Task;
import com.github.novicezk.midjourney.util.MimeTypeUtils;
import eu.maxschuster.dataurl.DataUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {
	private final TaskStoreService taskStoreService;
	private final DiscordLoadBalancer discordLoadBalancer;

	@Override
	public SubmitResultVO submitImagine(Task task, List<DataUrl> dataUrls) {
		DiscordInstance instance = this.discordLoadBalancer.chooseInstance();
		if (instance == null) {
			return SubmitResultVO.fail(ReturnCode.NOT_FOUND, "无可用的账号实例");
		}
		task.setProperty(Constants.TASK_PROPERTY_DISCORD_INSTANCE_ID, instance.getInstanceId());
		return instance.submitTask(task, () -> {
			List<String> imageUrls = new ArrayList<>();
			for (DataUrl dataUrl : dataUrls) {
				String taskFileName = task.getId() + "." + MimeTypeUtils.guessFileSuffix(dataUrl.getMimeType());
				Message<String> uploadResult = instance.upload(taskFileName, dataUrl);
				if (uploadResult.getCode() != ReturnCode.SUCCESS) {
					return Message.of(uploadResult.getCode(), uploadResult.getDescription());
				}
				String finalFileName = uploadResult.getResult();
				Message<String> sendImageResult = instance.sendImageMessage("upload image: " + finalFileName, finalFileName);
				if (sendImageResult.getCode() != ReturnCode.SUCCESS) {
					return Message.of(sendImageResult.getCode(), sendImageResult.getDescription());
				}
				imageUrls.add(sendImageResult.getResult());
			}
			if (!imageUrls.isEmpty()) {
				task.setPrompt(String.join(" ", imageUrls) + " " + task.getPrompt());
				task.setPromptEn(String.join(" ", imageUrls) + " " + task.getPromptEn());
				task.setDescription("/imagine " + task.getPrompt());
				this.taskStoreService.save(task);
			}
			return instance.imagine(task.getPromptEn(), task.getPropertyGeneric(Constants.TASK_PROPERTY_NONCE));
		});
	}

	@Override
	public SubmitResultVO submitUpscale(Task task, String targetMessageId, String targetMessageHash, int index, int messageFlags) {
		String instanceId = task.getPropertyGeneric(Constants.TASK_PROPERTY_DISCORD_INSTANCE_ID);
		DiscordInstance discordInstance = this.discordLoadBalancer.getDiscordInstance(instanceId);
		if (discordInstance == null || !discordInstance.isAlive()) {
			return SubmitResultVO.fail(ReturnCode.NOT_FOUND, "账号不可用: " + instanceId);
		}
		return discordInstance.submitTask(task, () -> discordInstance.upscale(targetMessageId, index, targetMessageHash, messageFlags, task.getPropertyGeneric(Constants.TASK_PROPERTY_NONCE)));
	}

	@Override
	public SubmitResultVO submitVariation(Task task, String targetMessageId, String targetMessageHash, int index, int messageFlags) {
		String instanceId = task.getPropertyGeneric(Constants.TASK_PROPERTY_DISCORD_INSTANCE_ID);
		DiscordInstance discordInstance = this.discordLoadBalancer.getDiscordInstance(instanceId);
		if (discordInstance == null || !discordInstance.isAlive()) {
			return SubmitResultVO.fail(ReturnCode.NOT_FOUND, "账号不可用: " + instanceId);
		}
		return discordInstance.submitTask(task, () -> discordInstance.variation(targetMessageId, index, targetMessageHash, messageFlags, task.getPropertyGeneric(Constants.TASK_PROPERTY_NONCE)));
	}

	@Override
	public SubmitResultVO submitReroll(Task task, String targetMessageId, String targetMessageHash, int messageFlags) {
		String instanceId = task.getPropertyGeneric(Constants.TASK_PROPERTY_DISCORD_INSTANCE_ID);
		DiscordInstance discordInstance = this.discordLoadBalancer.getDiscordInstance(instanceId);
		if (discordInstance == null || !discordInstance.isAlive()) {
			return SubmitResultVO.fail(ReturnCode.NOT_FOUND, "账号不可用: " + instanceId);
		}
		return discordInstance.submitTask(task, () -> discordInstance.reroll(targetMessageId, targetMessageHash, messageFlags, task.getPropertyGeneric(Constants.TASK_PROPERTY_NONCE)));
	}

	@Override
	public SubmitResultVO submitDescribe(Task task, DataUrl dataUrl) {
		DiscordInstance discordInstance = this.discordLoadBalancer.chooseInstance();
		if (discordInstance == null) {
			return SubmitResultVO.fail(ReturnCode.NOT_FOUND, "无可用的账号实例");
		}
		task.setProperty(Constants.TASK_PROPERTY_DISCORD_INSTANCE_ID, discordInstance.getInstanceId());
		return discordInstance.submitTask(task, () -> {
			String taskFileName = task.getId() + "." + MimeTypeUtils.guessFileSuffix(dataUrl.getMimeType());
			Message<String> uploadResult = discordInstance.upload(taskFileName, dataUrl);
			if (uploadResult.getCode() != ReturnCode.SUCCESS) {
				return Message.of(uploadResult.getCode(), uploadResult.getDescription());
			}
			String finalFileName = uploadResult.getResult();
			return discordInstance.describe(finalFileName, task.getPropertyGeneric(Constants.TASK_PROPERTY_NONCE));
		});
	}

	@Override
	public SubmitResultVO submitBlend(Task task, List<DataUrl> dataUrls, BlendDimensions dimensions) {
		DiscordInstance discordInstance = this.discordLoadBalancer.chooseInstance();
		if (discordInstance == null) {
			return SubmitResultVO.fail(ReturnCode.NOT_FOUND, "无可用的账号实例");
		}
		task.setProperty(Constants.TASK_PROPERTY_DISCORD_INSTANCE_ID, discordInstance.getInstanceId());
		return discordInstance.submitTask(task, () -> {
			List<String> finalFileNames = new ArrayList<>();
			for (DataUrl dataUrl : dataUrls) {
				String taskFileName = task.getId() + "." + MimeTypeUtils.guessFileSuffix(dataUrl.getMimeType());
				Message<String> uploadResult = discordInstance.upload(taskFileName, dataUrl);
				if (uploadResult.getCode() != ReturnCode.SUCCESS) {
					return Message.of(uploadResult.getCode(), uploadResult.getDescription());
				}
				finalFileNames.add(uploadResult.getResult());
			}
			return discordInstance.blend(finalFileNames, dimensions, task.getPropertyGeneric(Constants.TASK_PROPERTY_NONCE));
		});
	}

	@Override
	public SubmitResultVO submitZoom(Task task, String targetMessageId, String targetMessageHash, int messageFlags) {
		String instanceId = task.getPropertyGeneric(Constants.TASK_PROPERTY_DISCORD_INSTANCE_ID);
		DiscordInstance discordInstance = this.discordLoadBalancer.getDiscordInstance(instanceId);
		if (discordInstance == null || !discordInstance.isAlive()) {
			return SubmitResultVO.fail(ReturnCode.NOT_FOUND, "账号不可用: " + instanceId);
		}

		if(Set.of(TaskAction.ZOOM_1,TaskAction.ZOOM_2).contains(task.getAction())){
			return discordInstance.submitTask(task, () -> {
				String zoomOut;
				if(task.getAction().equals(TaskAction.ZOOM_1)){
					zoomOut="75";
				} else if (task.getAction().equals(TaskAction.ZOOM_2)) {
					zoomOut = "50";
				}else {
					zoomOut = "50";
				}
				return discordInstance.zoom(targetMessageId, targetMessageHash, task.getPropertyGeneric(Constants.TASK_PROPERTY_NONCE),zoomOut);
			});
		}else {
			return discordInstance.submitTask(task, () -> {
				String upscaleParam = "upsample_v5_2x";
				if(TaskAction.UP2.equals(task.getAction())){

				}else if(TaskAction.UP4.equals(task.getAction())){
					upscaleParam = "upsample_v5_4x";
				}
				return discordInstance.upscale(targetMessageId, targetMessageHash, task.getPropertyGeneric(Constants.TASK_PROPERTY_NONCE),upscaleParam);
			});
		}

	}

	@Override
	public SubmitResultVO submitVary(Task task, String targetMessageId, String targetMessageHash, int messageFlags) {
		String instanceId = task.getPropertyGeneric(Constants.TASK_PROPERTY_DISCORD_INSTANCE_ID);
		DiscordInstance discordInstance = this.discordLoadBalancer.getDiscordInstance(instanceId);
		if (discordInstance == null || !discordInstance.isAlive()) {
			return SubmitResultVO.fail(ReturnCode.NOT_FOUND, "账号不可用: " + instanceId);
		}
		return discordInstance.submitTask(task, () -> {
			String vary;
			if (task.getAction().equals(TaskAction.VARY_HIGH)) {
				vary = "high_variation";
			} else {
				vary = "low_variation";
			}
			return discordInstance.vary(targetMessageId, targetMessageHash, task.getPropertyGeneric(Constants.TASK_PROPERTY_NONCE), vary);
		});
	}

	@Override
	public SubmitResultVO move(Task task, String targetMessageId, String targetMessageHash, int messageFlags) {
		String instanceId = task.getPropertyGeneric(Constants.TASK_PROPERTY_DISCORD_INSTANCE_ID);
		DiscordInstance discordInstance = this.discordLoadBalancer.getDiscordInstance(instanceId);
		if (discordInstance == null || !discordInstance.isAlive()) {
			return SubmitResultVO.fail(ReturnCode.NOT_FOUND, "账号不可用: " + instanceId);
		}
		return discordInstance.submitTask(task, () -> {
			String move = "pan_up";
			if (task.getAction().equals(TaskAction.MOVE_UP)) {
				move = "pan_up";
			} else if (task.getAction().equals(TaskAction.MOVE_DOWN)) {
				move = "pan_down";
			}if (task.getAction().equals(TaskAction.MOVE_LEFT)) {
				move = "pan_left";
			}if (task.getAction().equals(TaskAction.MOVE_RIGHT)) {
				move = "pan_right";
			}
			return discordInstance.move(targetMessageId, targetMessageHash, task.getPropertyGeneric(Constants.TASK_PROPERTY_NONCE), move);
		});
	}
}
