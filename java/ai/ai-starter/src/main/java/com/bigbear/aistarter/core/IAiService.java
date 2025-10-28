
package com.bigbear.aistarter.core;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface IAiService {

    @SystemMessage("{{sm}}")
    TokenStream streemChatWithSystem(@MemoryId String memoryId, @V("sm") String systemMessage,
        @UserMessage String prompt, @UserMessage String userMessage);

    TokenStream streemChat(@MemoryId String memoryId, @UserMessage String prompt,
        @UserMessage String userMessage);

    /**
     * 带角色设定的流式输出
     *
     * @param systemMessage String
     * @param userMessage   String
     * @return TokenStream
     */
    @SystemMessage("{{message}}")
    TokenStream streemChatWithSystem(@MemoryId String memoryId, @V("message") String systemMessage,
        @UserMessage String userMessage);


    @SystemMessage("{{sm}}")
    String chatWithSystem(@MemoryId String memoryId, @V("sm") String systemMessage,
        @UserMessage String prompt, @UserMessage String userMessage);

    @SystemMessage("{{sm}}")
    String chatWithSystem(@MemoryId String memoryId, @V("sm") String systemMessage,
        @UserMessage String userMessage);
}
