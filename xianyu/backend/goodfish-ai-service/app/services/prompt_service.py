"""Sole prompt assembly point (ContextAssembler).

Decision A: the product's ``system_prompt`` IS the final system prompt —
the operator edits it in the admin console (and can start from a template),
so this service must never substitute a second built-in template, which
would silently override the operator's configuration.

Assembly rules:
1. take ``system_prompt`` as the base text (empty is rejected upstream as 40402);
2. replace product variables and the buyer question;
3. if the base has no ``{{question}}`` placeholder, append the question
   section so the model always sees what the buyer asked;
4. append the three scenario prompts, but only the non-empty ones.
"""

from dataclasses import dataclass

from ..constants import (
    DEFAULT_MAX_REPLY_LENGTH,
    PROMPT_QUESTION_FALLBACK_TEMPLATE,
    PROMPT_SCENARIO_DEFAULT_TITLE,
    PROMPT_SCENARIO_PRICE_TITLE,
    PROMPT_SCENARIO_TECH_TITLE,
    PROMPT_SECTION_SCENARIO,
    PROMPT_SECTION_SEPARATOR,
    PROMPT_VAR_DEFAULT_PROMPT,
    PROMPT_VAR_ITEM_DESC,
    PROMPT_VAR_ITEM_PRICE,
    PROMPT_VAR_ITEM_TITLE,
    PROMPT_VAR_MAX_REPLY_LENGTH,
    PROMPT_VAR_PRICE_PROMPT,
    PROMPT_VAR_QUESTION,
    PROMPT_VAR_TECH_PROMPT,
)


@dataclass(frozen=True)
class PromptInputs:
    system_prompt: str
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
        """Render the product prompt with variables, question and scenarios."""
        base = (inputs.system_prompt or "").strip()
        has_question_placeholder = PROMPT_VAR_QUESTION in base

        replacements = {
            PROMPT_VAR_ITEM_TITLE: inputs.item_title or "",
            PROMPT_VAR_ITEM_DESC: inputs.item_desc or "",
            PROMPT_VAR_ITEM_PRICE: inputs.item_price or "",
            PROMPT_VAR_MAX_REPLY_LENGTH: self._resolve_max_reply_length(
                inputs.max_reply_length
            ),
            PROMPT_VAR_QUESTION: inputs.question,
            PROMPT_VAR_PRICE_PROMPT: inputs.price_prompt or "",
            PROMPT_VAR_TECH_PROMPT: inputs.tech_prompt or "",
            PROMPT_VAR_DEFAULT_PROMPT: inputs.default_prompt or "",
        }
        rendered = base
        for placeholder, value in replacements.items():
            rendered = rendered.replace(placeholder, value)

        if not has_question_placeholder:
            # The operator's prompt may omit the placeholder entirely; without
            # this fallback the model would receive no buyer question at all.
            rendered = (
                f"{rendered}{PROMPT_SECTION_SEPARATOR}"
                f"{PROMPT_QUESTION_FALLBACK_TEMPLATE}{inputs.question}"
            )

        scenario_section = self._build_scenario_section(inputs)
        if scenario_section:
            rendered = f"{rendered}{PROMPT_SECTION_SEPARATOR}{scenario_section}"
        return rendered

    @staticmethod
    def _resolve_max_reply_length(value: int) -> str:
        if value and value > 0:
            return str(value)
        return str(DEFAULT_MAX_REPLY_LENGTH)

    @staticmethod
    def _build_scenario_section(inputs: PromptInputs) -> str:
        """Return the scenario section, or an empty string when all are blank.

        This batch keeps every scenario prompt blank in the admin console, so
        the section is normally absent; it appears once those fields are filled.
        """
        entries = (
            (PROMPT_SCENARIO_PRICE_TITLE, inputs.price_prompt),
            (PROMPT_SCENARIO_TECH_TITLE, inputs.tech_prompt),
            (PROMPT_SCENARIO_DEFAULT_TITLE, inputs.default_prompt),
        )
        blocks = [
            f"{title}\n{prompt.strip()}"
            for title, prompt in entries
            if prompt and prompt.strip()
        ]
        if not blocks:
            return ""
        return f"{PROMPT_SECTION_SCENARIO}\n{PROMPT_SECTION_SEPARATOR.join(blocks)}"
