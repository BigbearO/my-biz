import __config from "@/config/env";

export function getSnowflakeId(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: __config.basePath + "/snowflake-id",
      method: "GET",
      header: { "Content-Type": "application/json" },
      success(res) {
        if (res.statusCode === 200) {
          resolve(res.data as string);
        } else {
          reject(res.data);
        }
      },
      fail(error) {
        console.error("获取会话ID失败", error);
        reject(error);
      },
    });
  });
}

export interface ChatRequest {
  message: string;
  conversationId: string;
}

export interface QuickOption {
  label: string;
  value: string;
}

export interface SummaryFields {
  chiefComplaint: string;
  presentIllness: string;
  pastHistory: string;
  medication: string;
  allergy: string;
  visitGoal: string;
}

export interface SseChatEvent {
  type: "chunk" | "options" | "phase" | "summary" | "done" | "error";
  content?: string;
  items?: QuickOption[];
  value?: string;
  completed?: boolean;
  audio?: string;
  message?: string;
  fields?: SummaryFields;
}
