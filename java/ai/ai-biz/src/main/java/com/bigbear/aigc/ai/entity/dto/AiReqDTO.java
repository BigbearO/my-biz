package com.bigbear.aigc.ai.entity.dto;

import com.bigbear.aigc.ai.enums.AiContentEnums;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Accessors(chain = true)
public class AiReqDTO {

	/*
	'{
    "model": "qwen-vl-max-latest",
    "messages": [
        {
            "role": "user",
            "content": [
                {
                    "type": "video",
                    "video": [
                        "https://img.alicdn.com/imgextra/i3/O1CN01K3SgGo1eqmlUgeE9b_!!6000000003923-0-tps-3840-2160.jpg",
                        "https://img.alicdn.com/imgextra/i4/O1CN01BjZvwg1Y23CF5qIRB_!!6000000003000-0-tps-3840-2160.jpg",
                        "https://img.alicdn.com/imgextra/i4/O1CN01Ib0clU27vTgBdbVLQ_!!6000000007859-0-tps-3840-2160.jpg",
                        "https://img.alicdn.com/imgextra/i1/O1CN01aygPLW1s3EXCdSN4X_!!6000000005710-0-tps-3840-2160.jpg"
                    ]
                },
                {
                    "type": "text",
                    "text": "描述这个视频的具体过程"
                }
            ]
        }
    ]
    */

	/*  'Content-Type: application/json' \
-d '{
    "model": "qwen-vl-max",
    "messages": [{
        "role": "user",
        "content": [{
                "type": "video_url",
                "video_url": {
                    "url": "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20241115/cqqkru/1.mp4"
                }
            },
            {
                "type": "text",
                "text": "视频的内容是什么?"
            }
        ]
    }]
}'
*/

	private String model = "qwen-vl-max";

	private List<MessageBody> messages;


	@Getter
	@Setter
	@Accessors(chain = true)
	public static class MessageBody {
		private String role = "user";
		//由于里面的数据多变，直接使用map存储,type 目前有image_url, "text",video,video_url,其中videoUrl是
		private List<Map<String,Object>> content;
	}

	public static AiReqDTO mediaFromUrl(AiContentEnums media , String url, String question){
		AiReqDTO reqDTO=new AiReqDTO();
		// 媒体类型的模型不用变
		reqDTO.setMessages(new ArrayList<>());

		Map<String,Object> mediaContent=new HashMap<>();
		mediaContent.put("type",media.getName());
		mediaContent.put(media.getName(),url);

		Map<String,Object> questionContent=new HashMap<>();
		questionContent.put("type",AiContentEnums.TEXT.getName());
		questionContent.put(AiContentEnums.TEXT.getName(),question);

		List<Map<String,Object>> contentList =new ArrayList<>();
		contentList.add(mediaContent);
		contentList.add(questionContent);

		MessageBody messageBody=new MessageBody();
		messageBody.setContent(contentList);
		reqDTO.getMessages().add(messageBody);

		return reqDTO;
	}

}
