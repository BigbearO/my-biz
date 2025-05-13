package com.bigbear.aigc.ai.controller;

import com.alibaba.fastjson.JSONObject;
import com.bigbear.aigc.ai.service.ChatService;
import com.bigbear.aigc.ai.service.MediaContentRecognition;
import com.bigbear.aigc.ai.service.PetDoctor;
import com.pig4cloud.pig.common.core.util.R;
import com.pig4cloud.pig.common.security.annotation.Inner;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.TokenStream;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/test")
@Tag(description = "test", name = "test")
@Inner(value = false)
public class TestController {


	private final ChatService chatService;

	private final MediaContentRecognition mediaContentRecognition;

	private final PetDoctor petDoctor;

	@jakarta.annotation.Resource(name = "myStreamingModel")
	private StreamingChatLanguageModel myStreamingModel;

	@RequestMapping("/chat")
	@Inner(value = false)
	public R<?> chat(@RequestParam String question) {
		return R.ok(petDoctor.chat(1L,question));
	}

	@RequestMapping("/contentRecognition")
	@Inner(value = false)
	public R<?> contentRecognition(@RequestParam String url, @RequestParam(required = false, defaultValue = "0")
	Integer type, @RequestParam String question) {
		url = url.trim();
		url = url.replace(" ", "");
		return R.ok(mediaContentRecognition.contentByWebUrl(type,url, question));
	}

	@RequestMapping("/test")
	@Inner(value = false)
	public R<?> test() {
		String res = "";
		try {
			res = mediaContentRecognition.contentByBytes("提取图片中 9月30号的上证指数点数", null);
		} catch (IOException e) {

		}
		return R.ok(res);
	}

	//前端用下面
	 /*

	 <template>
  <div>
    <h1>实时时间</h1>
    <p>{{ realTime }}</p>
  </div>
</template>

<script>
export default {
  data() {
    return {
      realTime: '',
    };
  },
  mounted() {
    this.connectSSE();
  },
  methods: {
    connectSSE() {
      const eventSource = new EventSource('/events');

      eventSource.onmessage = (event) => {
        this.realTime = event.data;
      };

      eventSource.onerror = (error) => {
        console.error('EventSource failed:', error);
        eventSource.close();
      };
    },
  },
  beforeDestroy() {
    if (this.eventSource) {
      this.eventSource.close();
    }
  },
};
</script>

	  */
	//前端用上面

	@RequestMapping("/testStream")
	@Inner(value = false)
	public R<?> testStream(String question) {

		String res = "";
		ChatMessage userMessage = AiMessage.from(question);
		SseEmitter emitter = new SseEmitter();

		StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

			@Override
			public void onPartialResponse(String s) {
				sendSseData(emitter, s);
			}

			@Override
			public void onCompleteResponse(ChatResponse chatResponse) {
				completeSse(emitter);
			}

			@Override
			public void onError(Throwable throwable) {
				handleError(emitter, throwable);
			}
		};
		List<ChatMessage> list = Arrays.asList(userMessage);
		myStreamingModel.chat(list, handler);


		return R.ok(res);
	}

//	前端还是用sse
	@RequestMapping("/testFlux")
	@Inner(value = false)
	public Flux<String> testFlux(String question, HttpServletResponse response) {
		response.setCharacterEncoding("UTF-8");
		return Flux.create(sink -> {
			myStreamingModel.chat(question, new StreamingChatResponseHandler() {
				@Override
				public void onPartialResponse(String partialResponse) {
					log.info("onPartialResponse:{}", partialResponse);
					sink.next(partialResponse);
				}

				@Override
				public void onCompleteResponse(ChatResponse completeResponse) {
					log.info("complete:{}", completeResponse);
					sink.complete();
				}

				@Override
				public void onError(Throwable error) {
					sink.error(error);
				}
			});

		});
	}

	private void sendSseData(SseEmitter sseEmitter, String result) {
		try {
			JSONObject jsonObject =  new JSONObject();
			jsonObject.put("text",result);
			sseEmitter.send(jsonObject, MediaType.APPLICATION_JSON);
		} catch (IOException e) {
			log.warn("SSE 发送数据失败:{}", e.getMessage());
		}
	}

	private void handleError(SseEmitter sseEmitter, Throwable throwable) {
		log.error("SSE 发生错误:{}", throwable.getMessage());
		sseEmitter.completeWithError(throwable);
	}

	private void completeSse(SseEmitter sseEmitter) {
		try {
			sseEmitter.send("[DONE]");
			sseEmitter.complete();
		} catch (Exception e) {
			log.warn("SSE 关闭失败:{}", e.getMessage());
		}
	}
}
