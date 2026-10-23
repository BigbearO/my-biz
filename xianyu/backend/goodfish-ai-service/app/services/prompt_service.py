"""Sole prompt assembly point (ContextAssembler).

Renders the unified system prompt template: product variables, the three
scenario prompts embedded verbatim (never selected by intent), and the
buyer question appended at the end. No other module may assemble prompts.
"""

from dataclasses import dataclass

from ..constants import (
    DEFAULT_MAX_REPLY_LENGTH,
    PROMPT_VAR_DEFAULT_PROMPT,
    PROMPT_VAR_ITEM_DESC,
    PROMPT_VAR_ITEM_PRICE,
    PROMPT_VAR_ITEM_TITLE,
    PROMPT_VAR_MAX_REPLY_LENGTH,
    PROMPT_VAR_PRICE_PROMPT,
    PROMPT_VAR_QUESTION,
    PROMPT_VAR_TECH_PROMPT,
)

SYSTEM_PROMPT_TEMPLATE = """你是一位专业的闲鱼客服AI助手。请根据用户消息和上下文，直接生成合适的回复。

## 核心原则
1. 准确理解意图：只根据用户实际说的内容判断，不要过度解读
2. 不要主动提及敏感话题：用户没提到的（如退款、砍价）不要主动提
3. 基于商品信息回答：只回答商品信息中有的内容
4. 避免重复：结合对话历史，不要重复之前说过的话
5. 语言简洁友好：回复要自然、简短，尽量不超过{{max_reply_length}}个字

## 商品信息
标题：{{item_title}}
描述：{{item_desc}}
价格：{{item_price}}

## 场景处理指南
### 当用户明确要求降价/优惠/砍价时
{{price_prompt}}

### 当用户询问产品技术/功能/使用问题时
{{tech_prompt}}

### 其他一般咨询（物流、售后、商品介绍等）
{{default_prompt}}

### 当用户闲聊或话题偏离商品时
- 简短友好地回应一句，不冷落用户，也不跟着闲聊下去
- 随即自然地把话题引导回当前商品，例如介绍商品亮点、询问是否想了解商品细节
- 严禁主动透露或暗示自己是AI、机器人、自动回复程序

## 特别注意
- 用户只是问价格≠用户在砍价，正常回答价格即可
- 用户咨询售后≠用户要退款，正常解答即可
- 如果用户的问题超过你的回答范围，比如发图片，可以说"等等，这个问题我需要看看"

## 买家问题
基于上述，回复买家。问题如下：
{{question}}

请直接输出回复内容，不要输出分析过程。"""


@dataclass(frozen=True)
class PromptInputs:
    item_title: str
    item_desc: str
    item_price: str
    max_reply_length: int
    price_prompt: str
    tech_prompt: str
    default_prompt: str
    question: str


class PromptService:
    def render_system_prompt(self, inputs: PromptInputs) -> str:
        """Variable replacement; empty scenario prompts stay empty strings."""
        replacements = {
            PROMPT_VAR_ITEM_TITLE: inputs.item_title,
            PROMPT_VAR_ITEM_DESC: inputs.item_desc,
            PROMPT_VAR_ITEM_PRICE: inputs.item_price,
            PROMPT_VAR_MAX_REPLY_LENGTH: str(
                inputs.max_reply_length
                if inputs.max_reply_length and inputs.max_reply_length > 0
                else DEFAULT_MAX_REPLY_LENGTH
            ),
            PROMPT_VAR_PRICE_PROMPT: inputs.price_prompt or "",
            PROMPT_VAR_TECH_PROMPT: inputs.tech_prompt or "",
            PROMPT_VAR_DEFAULT_PROMPT: inputs.default_prompt or "",
            PROMPT_VAR_QUESTION: inputs.question,
        }
        rendered = SYSTEM_PROMPT_TEMPLATE
        for placeholder, value in replacements.items():
            rendered = rendered.replace(placeholder, value)
        return rendered
