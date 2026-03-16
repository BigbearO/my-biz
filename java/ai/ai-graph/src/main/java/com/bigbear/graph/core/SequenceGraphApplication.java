package com.bigbear.graph.core;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;


import java.util.Map;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;

public class SequenceGraphApplication {
    public static void main(String[] args) throws GraphStateException {
        StateGraph<AgentState> sequenceGraph = getSequenceGraph();

        //打印图的mermaid代码
        System.out.println(sequenceGraph.getGraph(GraphRepresentation.Type.MERMAID, "Sequence Graph", true).content());

        //编译图并运行（初始数据值为 test->test-init-value
        sequenceGraph
            .compile() //编译
            .invoke(Map.of("test", "test-init-value")) //运行（并赋初始值)
            .ifPresent(c -> {
                //运行结束后，会执行本段逻辑
                System.out.println(c.data());
            });

    }

    public static StateGraph<AgentState> getSequenceGraph() throws GraphStateException {
        return new StateGraph<>(AgentState::new)
            //向图中添加2个节点
            .addNode("node-1", node_async(new Node1Action()))
            .addNode("node-2", node_async(new Node2Action()))
            //加一条边（开始->node1)
            .addEdge(GraphDefinition.START, "node-1")
            //加1条边(node1->node2)
            .addEdge("node-1", "node-2")
            //加1条边(node2->结束)
            .addEdge("node-2", GraphDefinition.END);
    }
}
