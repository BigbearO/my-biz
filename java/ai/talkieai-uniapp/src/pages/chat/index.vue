<template>
  <view class="chat-box">
    <CommonHeader background-color="#fff" :leftIcon="false" title="诊前助手">
      <template v-slot:content>
        <view class="header-title">诊前助手</view>
      </template>
      <template v-slot:right>
        <view class="new-chat-btn" @tap="handleNewChat">
          <image class="new-chat-icon" src="/static/clear.png" />
        </view>
      </template>
    </CommonHeader>

    <!-- 阶段指示器 -->
    <view class="phase-bar">
      <text class="phase-text">{{ phaseLabel }}</text>
    </view>

    <!-- 聊天内容 -->
    <scroll-view
      class="chat-container"
      scroll-y
      :scroll-into-view="scrollIntoViewId"
      :scroll-with-animation="true"
    >
      <view v-for="(msg, index) in messages" :key="index" class="message-item">
        <!-- 用户消息 -->
        <view v-if="msg.role === 'user'" class="msg-user">
          <view class="msg-bubble user-bubble">{{ msg.content }}</view>
        </view>

        <!-- 助手消息 -->
        <view v-else class="msg-assistant">
          <view class="msg-bubble assistant-bubble">
            <text>{{ msg.content }}</text>
            <text v-if="isStreaming && msg === currentAssistantMsg" class="cursor">|</text>
          </view>

          <!-- 音频控制按钮 -->
          <view
            v-if="msg.audioBase64 && !isStreaming"
            class="audio-controls"
          >
            <view class="audio-btn" @tap="toggleAudio(index)">
              <text v-if="msg.audioState === 'playing'" class="audio-icon">&#9646;&#9646;</text>
              <text v-else class="audio-icon">&#9654;</text>
              <text class="audio-label">
                {{ msg.audioState === 'playing' ? '暂停' : msg.audioState === 'paused' ? '继续' : '播放' }}
              </text>
            </view>
            <view class="audio-btn" @tap="replayAudio(index)">
              <text class="audio-icon">&#8634;</text>
              <text class="audio-label">重播</text>
            </view>
          </view>

          <!-- 选项按钮 -->
          <view
            v-if="msg.options && msg.options.length > 0 && !isStreaming"
            class="options-container"
          >
            <view
              v-for="(opt, oi) in msg.options"
              :key="oi"
              class="option-btn"
              @tap="onOptionClick(opt)"
            >
              {{ opt.label }}
            </view>
          </view>
        </view>
      </view>
      <view id="scroll-bottom" style="height: 1px;"></view>
    </scroll-view>

    <!-- 摘要卡片 -->
    <view v-if="summary.text" class="summary-card">
      <view class="summary-title">病情摘要</view>
      <template v-if="summary.fields">
        <view class="summary-field" v-for="(val, key) in summary.fields" :key="key">
          <text class="field-label">{{ FIELD_LABELS[key as string] || key }}</text>
          <text class="field-value">{{ val }}</text>
        </view>
      </template>
      <text v-else class="summary-content">{{ summary.text }}</text>
    </view>

    <!-- 底部输入区域 -->
    <view class="input-area">
      <!-- 文字模式 -->
      <template v-if="!voiceMode">
        <input
          v-model="inputText"
          class="chat-input"
          placeholder="请描述您的症状..."
          :disabled="isStreaming"
          confirm-type="send"
          @confirm="sendMessage(inputText)"
        />
        <view class="mode-switch-btn" @tap="voiceMode = true" v-if="!isStreaming">
          <image class="switch-icon" src="/static/icon_voice.png" />
        </view>
        <view
          class="send-btn"
          :class="{ active: inputText.trim() && !isStreaming }"
          @tap="sendMessage(inputText)"
        >
          <image class="send-icon" src="/static/icon_send.png" />
        </view>
      </template>

      <!-- 语音模式 -->
      <template v-else>
        <view class="voice-input-area">
          <view
            class="hold-to-talk"
            :class="{ recording: isRecording }"
            @tap="onTapVoice"
          >
            <template v-if="!isRecording">
              <image class="voice-icon" src="/static/icon_voice_fixed.png" />
              <text class="hint">点击 录音</text>
            </template>
            <template v-else>
              <view class="recording-indicator" />
              <text class="hint">点击 停止</text>
            </template>
          </view>
          <view class="mode-switch-btn" @tap="voiceMode = false">
            <image class="switch-icon keyboard" src="/static/icon_keyboard.png" />
          </view>
        </view>
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import CommonHeader from "@/components/CommonHeader.vue";
import { ref, computed, nextTick } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { getSnowflakeId } from "@/api/preconsult";
import type { QuickOption, SseChatEvent, SummaryFields } from "@/api/preconsult";
import __config from "@/config/env";
import speech from "@/components/speechExecuter";

type AudioState = "idle" | "playing" | "paused";

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
  options: QuickOption[];
  phase?: string;
  audioBase64?: string;
  audioState: AudioState;
}

const PHASE_LABELS: Record<string, string> = {
  GREETING: "了解症状",
  CHIEF_COMPLAINT: "聚焦主诉",
  HPI: "详细询问",
  MEDICAL_HISTORY: "健康筛查",
  GOAL: "就诊目标",
  SUMMARY: "生成摘要",
};

const FIELD_LABELS: Record<string, string> = {
  chiefComplaint: "主诉",
  presentIllness: "现病史",
  pastHistory: "既往史",
  medication: "用药情况",
  allergy: "过敏史",
  visitGoal: "就诊诉求",
};

const messages = ref<ChatMessage[]>([]);
const inputText = ref("");
const isStreaming = ref(false);
const currentPhase = ref("");
const summary = ref<{ text: string; fields?: SummaryFields }>({ text: "" });
const conversationId = ref("");
const currentAssistantMsg = ref<ChatMessage | null>(null);
const scrollIntoViewId = ref("");

// 语音输入状态
const voiceMode = ref(false);
const isRecording = ref(false);

// 音频实例管理：key = 消息在 messages 数组中的 index
const audioInstances = ref<Map<number, any>>(new Map());

const phaseLabel = computed(() => {
  return PHASE_LABELS[currentPhase.value] || "诊前问诊";
});

onLoad(async () => {
  await initConversation();
});

async function initConversation() {
  try {
    const id = await getSnowflakeId();
    conversationId.value = id;
  } catch (e) {
    conversationId.value =
      "conv_" + Date.now() + "_" + Math.random().toString(36).substring(2, 11);
  }
  // 销毁所有音频实例
  destroyAllAudioInstances();
  messages.value = [];
  summary.value = { text: "" };
  currentPhase.value = "";

  // 欢迎语
  messages.value.push({
    role: "assistant",
    content: "你好，我是小护！花几分钟问几个问题，帮您梳理一下病情，方便医生更快了解您的情况。您现在的症状是什么？",
    options: [],
    audioState: "idle",
  });
}

async function handleNewChat() {
  if (isStreaming.value) return;
  uni.showModal({
    title: "开始新会话",
    content: "当前对话将被清空，是否继续？",
    success: async (res) => {
      if (res.confirm) {
        await initConversation();
      }
    },
  });
}

// 语音：点击录音/点击停止
function onTapVoice() {
  if (isRecording.value) {
    isRecording.value = false;
    speech.handleEndVoice();
  } else {
    isRecording.value = true;
    speech.handleVoiceStart({
      processing: () => {},
      success: ({ inputValue }: { inputValue: string }) => {
        isRecording.value = false;
        voiceMode.value = false;
        if (inputValue?.trim()) {
          sendMessage(inputValue.trim());
        }
      },
      cancel: () => {
        isRecording.value = false;
      },
      error: (err: any) => {
        isRecording.value = false;
        uni.showToast({ title: err?.message || "语音识别失败", icon: "none" });
      },
      interval: () => {},
    });
  }
}

function sendMessage(text: string) {
  if (!text || !text.trim() || isStreaming.value) return;

  const userMsg: ChatMessage = {
    role: "user",
    content: text.trim(),
    options: [],
    audioState: "idle",
  };
  messages.value.push(userMsg);

  currentAssistantMsg.value = {
    role: "assistant",
    content: "",
    options: [],
    audioState: "idle",
  };
  messages.value.push(currentAssistantMsg.value);

  isStreaming.value = true;
  inputText.value = "";
  scrollToBottom();

  requestSse(text.trim());
}

function requestSse(message: string) {
  // #ifdef H5
  requestSseH5(message);
  // #endif

  // #ifndef H5
  requestSseApp(message);
  // #endif
}

async function requestSseH5(message: string) {
  try {
    const response = await fetch(__config.basePath + "/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        message: message,
        conversationId: conversationId.value,
      }),
    });

    const reader = response.body!.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop()!;

      for (const line of lines) {
        if (line.startsWith("data:")) {
          const jsonStr = line.substring(5).trim();
          if (jsonStr && jsonStr !== "[DONE]") {
            try {
              handleSseEvent(JSON.parse(jsonStr));
            } catch (e) {
              console.error("SSE 解析失败", jsonStr);
            }
          }
        }
      }
    }
  } catch (e) {
    console.error("SSE 请求失败", e);
    isStreaming.value = false;
    uni.showToast({ title: "请求失败", icon: "none" });
  }
}

function requestSseApp(message: string) {
  uni.request({
    url: __config.basePath + "/chat",
    method: "POST",
    header: { "Content-Type": "application/json" },
    data: { message, conversationId: conversationId.value },
    responseType: "text",
    success: (res) => {
      const raw = (res.data as string) || "";
      const lines = raw.split("\n");
      for (const line of lines) {
        if (line.startsWith("data:")) {
          const jsonStr = line.substring(5).trim();
          if (jsonStr && jsonStr !== "[DONE]") {
            try {
              handleSseEvent(JSON.parse(jsonStr));
            } catch (e) {
              console.error("SSE 解析失败", jsonStr);
            }
          }
        }
      }
    },
    fail: (err) => {
      console.error("请求失败", err);
      isStreaming.value = false;
      uni.showToast({ title: "请求失败", icon: "none" });
    },
  });
}

function handleSseEvent(event: SseChatEvent) {
  switch (event.type) {
    case "chunk":
      if (currentAssistantMsg.value) {
        currentAssistantMsg.value.content += event.content || "";
      }
      scrollToBottom();
      break;

    case "options":
      if (currentAssistantMsg.value) {
        currentAssistantMsg.value.options = event.items || [];
      }
      break;

    case "phase":
      currentPhase.value = event.value || "";
      if (currentAssistantMsg.value) {
        currentAssistantMsg.value.phase = event.value;
      }
      break;

    case "summary":
      summary.value = {
        text: event.content || "",
        fields: event.fields,
      };
      break;

    case "done":
      isStreaming.value = false;

      // 将 audio base64 存入消息对象
      if (event.audio && currentAssistantMsg.value) {
        currentAssistantMsg.value.audioBase64 = event.audio;
        // 自动播放
        const msgIndex = messages.value.indexOf(currentAssistantMsg.value);
        if (msgIndex >= 0) {
          playAudioForMessage(msgIndex, event.audio);
          currentAssistantMsg.value.audioState = "playing";
        }
      }

      currentAssistantMsg.value = null;

      if (event.completed) {
        uni.showToast({ title: "问诊已完成", icon: "success" });
      }
      break;

    case "error":
      isStreaming.value = false;
      uni.showToast({ title: event.message || "出错了", icon: "none" });
      break;
  }
}

function onOptionClick(option: QuickOption) {
  inputText.value = option.value;
  sendMessage(option.value);
}

// ========== 音频控制 ==========

function pauseAllAudio() {
  messages.value.forEach((msg, idx) => {
    if (msg.audioState === "playing") {
      const instance = audioInstances.value.get(idx);
      if (instance) {
        try {
          instance.pause();
        } catch (e) {
          console.error("暂停音频失败", e);
        }
      }
      msg.audioState = "paused";
    }
  });
}

function destroyAudioInstance(index: number) {
  const instance = audioInstances.value.get(index);
  if (instance) {
    try {
      instance.stop();
      instance.destroy();
    } catch (e) {
      // ignore
    }
    audioInstances.value.delete(index);
  }
}

function destroyAllAudioInstances() {
  audioInstances.value.forEach((instance, idx) => {
    try {
      instance.stop();
      instance.destroy();
    } catch (e) {
      // ignore
    }
  });
  audioInstances.value.clear();
}

function toggleAudio(index: number) {
  const msg = messages.value[index];
  if (!msg || !msg.audioBase64) return;

  if (msg.audioState === "playing") {
    // 暂停当前
    const instance = audioInstances.value.get(index);
    if (instance) {
      try {
        instance.pause();
      } catch (e) {
        console.error("暂停失败", e);
      }
    }
    msg.audioState = "paused";
  } else {
    // 播放前先暂停其他
    pauseAllAudio();

    if (msg.audioState === "paused" && audioInstances.value.has(index)) {
      // 继续播放
      const instance = audioInstances.value.get(index);
      if (instance) {
        try {
          instance.play();
        } catch (e) {
          console.error("继续播放失败", e);
        }
      }
      msg.audioState = "playing";
    } else {
      // 从头播放
      destroyAudioInstance(index);
      playAudioForMessage(index, msg.audioBase64!);
      msg.audioState = "playing";
    }
  }
}

function replayAudio(index: number) {
  const msg = messages.value[index];
  if (!msg || !msg.audioBase64) return;

  pauseAllAudio();
  destroyAudioInstance(index);
  playAudioForMessage(index, msg.audioBase64!);
  msg.audioState = "playing";
}

function playAudioForMessage(index: number, base64: string) {
  // #ifdef H5
  playAudioH5(index, base64);
  // #endif

  // #ifdef APP-PLUS
  playAudioApp(index, base64);
  // #endif
}

function playAudioH5(index: number, base64: string) {
  try {
    const binaryStr = atob(base64);
    const bytes = new Uint8Array(binaryStr.length);
    for (let i = 0; i < binaryStr.length; i++) {
      bytes[i] = binaryStr.charCodeAt(i);
    }
    const blob = new Blob([bytes], { type: "audio/mpeg" });
    const url = URL.createObjectURL(blob);
    const audio = new Audio(url);

    audio.onended = () => {
      const msg = messages.value[index];
      if (msg) msg.audioState = "idle";
      URL.revokeObjectURL(url);
      audioInstances.value.delete(index);
    };

    audio.onerror = () => {
      const msg = messages.value[index];
      if (msg) msg.audioState = "idle";
      URL.revokeObjectURL(url);
      audioInstances.value.delete(index);
    };

    audioInstances.value.set(index, audio);
    audio.play();
  } catch (e) {
    console.error("H5 音频播放失败", e);
    const msg = messages.value[index];
    if (msg) msg.audioState = "idle";
  }
}

function playAudioApp(index: number, base64: string) {
  try {
    const fs = uni.getFileSystemManager();
    const filePath = `${wx.env.USER_DATA_PATH}/tts_${index}_${Date.now()}.mp3`;
    const buffer = uni.base64ToArrayBuffer(base64);
    fs.writeFile({
      filePath: filePath,
      data: buffer,
      encoding: "binary",
      success: () => {
        const innerAudio = uni.createInnerAudioContext();
        innerAudio.src = filePath;

        innerAudio.onEnded = () => {
          const msg = messages.value[index];
          if (msg) msg.audioState = "idle";
          audioInstances.value.delete(index);
          try {
            fs.unlink({ filePath: filePath });
          } catch (e) {
            // ignore
          }
        };

        innerAudio.onError = () => {
          const msg = messages.value[index];
          if (msg) msg.audioState = "idle";
          audioInstances.value.delete(index);
        };

        audioInstances.value.set(index, innerAudio);
        innerAudio.play();
      },
      fail: (err) => {
        console.error("写入音频文件失败", err);
        const msg = messages.value[index];
        if (msg) msg.audioState = "idle";
      },
    });
  } catch (e) {
    console.error("App 音频播放失败", e);
    const msg = messages.value[index];
    if (msg) msg.audioState = "idle";
  }
}

function scrollToBottom() {
  scrollIntoViewId.value = "";
  nextTick(() => {
    scrollIntoViewId.value = "scroll-bottom";
  });
}
</script>

<style lang="less" scoped>
.chat-box {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f5f5f5;
}

.header-title {
  font-size: 36rpx;
  font-weight: bold;
}

.new-chat-btn {
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;

  .new-chat-icon {
    width: 36rpx;
    height: 36rpx;
  }
}

.phase-bar {
  padding: 12rpx 32rpx;
  background-color: #e8f4fd;
  text-align: center;

  .phase-text {
    font-size: 24rpx;
    color: #1890ff;
  }
}

.chat-container {
  flex: 1;
  padding: 20rpx;
  height: 0;

  .message-item {
    margin-bottom: 24rpx;
  }

  .msg-user {
    display: flex;
    justify-content: flex-end;
  }

  .msg-assistant {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
  }

  .msg-bubble {
    max-width: 70%;
    padding: 20rpx 28rpx;
    border-radius: 16rpx;
    font-size: 28rpx;
    line-height: 1.6;
    word-break: break-all;
  }

  .user-bubble {
    background-color: #1890ff;
    color: #ffffff;
    border-top-right-radius: 4rpx;
  }

  .assistant-bubble {
    background-color: #ffffff;
    color: #333333;
    border-top-left-radius: 4rpx;
    box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
  }

  .cursor {
    animation: blink 1s infinite;
  }

  @keyframes blink {
    0%,
    50% {
      opacity: 1;
    }
    51%,
    100% {
      opacity: 0;
    }
  }

  .audio-controls {
    display: flex;
    gap: 16rpx;
    margin-top: 12rpx;

    .audio-btn {
      display: flex;
      align-items: center;
      gap: 6rpx;
      padding: 8rpx 20rpx;
      background-color: #ffffff;
      border: 2rpx solid #d9d9d9;
      border-radius: 24rpx;

      &:active {
        background-color: #f0f0f0;
      }

      .audio-icon {
        font-size: 24rpx;
        color: #1890ff;
      }

      .audio-label {
        font-size: 22rpx;
        color: #666666;
      }
    }
  }

  .options-container {
    display: flex;
    flex-wrap: wrap;
    gap: 16rpx;
    margin-top: 16rpx;
    max-width: 70%;

    .option-btn {
      padding: 12rpx 28rpx;
      background-color: #ffffff;
      border: 2rpx solid #1890ff;
      border-radius: 32rpx;
      color: #1890ff;
      font-size: 26rpx;

      &:active {
        background-color: #e8f4fd;
      }
    }
  }
}

.summary-card {
  margin: 20rpx;
  padding: 24rpx;
  background-color: #fff7e6;
  border: 2rpx solid #ffd591;
  border-radius: 16rpx;

  .summary-title {
    font-size: 30rpx;
    font-weight: bold;
    color: #d48806;
    margin-bottom: 12rpx;
  }

  .summary-content {
    font-size: 26rpx;
    color: #8c8c8c;
    line-height: 1.8;
  }

  .summary-field {
    margin-bottom: 16rpx;
    line-height: 1.6;

    .field-label {
      font-size: 24rpx;
      font-weight: bold;
      color: #d48806;
      display: block;
      margin-bottom: 4rpx;
    }

    .field-value {
      font-size: 26rpx;
      color: #666666;
    }
  }
}

.input-area {
  display: flex;
  padding: 16rpx 20rpx;
  background-color: #ffffff;
  border-top: 2rpx solid #e8e8e8;
  padding-bottom: calc(env(safe-area-inset-bottom) / 2 + 16rpx);

  .chat-input {
    flex: 1;
    height: 72rpx;
    padding: 0 24rpx;
    background-color: #f5f5f5;
    border-radius: 36rpx;
    font-size: 28rpx;
  }

  .mode-switch-btn {
    width: 72rpx;
    height: 72rpx;
    display: flex;
    align-items: center;
    justify-content: center;

    .switch-icon {
      width: 40rpx;
      height: 40rpx;

      &.keyboard {
        width: 44rpx;
        height: 44rpx;
      }
    }
  }

  .send-btn {
    margin-left: 16rpx;
    width: 80rpx;
    height: 72rpx;
    background-color: #d3d3d3;
    border-radius: 36rpx;
    display: flex;
    align-items: center;
    justify-content: center;

    &.active {
      background-color: #1890ff;
    }

    .send-icon {
      width: 32rpx;
      height: 32rpx;
    }
  }
}

.voice-input-area {
  display: flex;
  align-items: center;
  flex: 1;

  .hold-to-talk {
    flex: 1;
    height: 72rpx;
    background-color: #f5f5f5;
    border-radius: 36rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8rpx;
    user-select: none;

    &:active {
      background-color: #e0e0e0;
    }

    &.recording {
      background-color: #fce4e4;

      &:active {
        background-color: #f5caca;
      }
    }

    .voice-icon {
      width: 36rpx;
      height: 36rpx;
    }

    .hint {
      font-size: 28rpx;
      color: #666;
    }

    .recording-indicator {
      width: 16rpx;
      height: 16rpx;
      background-color: #ff4444;
      border-radius: 50%;
      animation: pulse-blink 1s infinite;
    }
  }
}

@keyframes pulse-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
