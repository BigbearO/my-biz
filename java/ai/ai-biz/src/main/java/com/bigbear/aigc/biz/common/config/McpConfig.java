package com.bigbear.aigc.biz.common.config;

import com.bigbear.aigc.biz.service.PetDoctor;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

//@Configuration
public class McpConfig {
    @Bean
    public McpTransport mcpTransport4stdio() {


        return new StdioMcpTransport.Builder()
//                .command(List.of("/usr/bin/npm", "exec", "@modelcontextprotocol/server-everything@0.6.2"))
//        这里使用stdio的方式来连接docker部署的GitHub MCP server
                .command(List.of("/usr/local/bin/docker", "run", "-e", "GITHUB_PERSONAL_ACCESS_TOKEN", "-i", "mcp/github"))
               .logEvents(true) // only if you want to see the traffic in the log
               .build();
    }


    @Bean
    @Primary
    public McpTransport streamableHttpMcpTransport() {
        // if you want to see the traffic in the log
        return new StreamableHttpMcpTransport.Builder()
                .logRequests(true) // if you want to see the traffic in the log
                .logResponses(true)
                .build();
    }



    @Bean
    public McpClient defaultMcpClient(McpTransport transport){
       return new DefaultMcpClient.Builder()
                .transport(transport)
//                 .logHandler(new MyLogMessageHandler())
                .build();

    }
    @Bean
    public ToolProvider t1(McpClient mcpClient){
      return     McpToolProvider.builder()

                .mcpClients(List.of(mcpClient))

                .build();
    }

    public void t(){

    }


    public PetDoctor petDoctor2(@Qualifier("myChatModel") ChatModel ChatModel,ToolProvider toolProvider,
                                ChatMemoryProvider chatMemoryProvider) {
        return AiServices.builder(PetDoctor.class)
                .chatModel(ChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .toolProvider(toolProvider)
                .build();
    }

    PetDoctor petDoctor2;
    // 示例
    public void test(){

        // petDoctor2 使用stdio的方式来连接docker部署的GitHub MCP server
        //       通过chat告诉LLM去总结LangChain4j这个github仓库最近的3个commits
        petDoctor2.chat("Summarize the last 3 commits of the LangChain4j GitHub repository");
        HttpServletResponse response=null;


    }
}
