package com.pennywiseai.tracker.data.service

/**
 * Chat template for the bundled Qwen 2.5 model, passed as
 * `ConversationConfig.chatTemplate` so it replaces the one baked into the
 * .litertlm file.
 *
 * Since LiteRT-LM 0.18 every message reaches the template with its content as
 * a list of parts (`[{"type": "text", "text": "..."}]`). The baked-in Qwen 2.5
 * template is the stock Hugging Face one, which joins `message.content` as a
 * string, so rendering fails with "tried to use + operator on unsupported
 * types string and sequence" (and, with tools, would print the system prompt
 * as a JSON list). This is the same template, with every content read through
 * [contentText] so it takes a string or a list of parts, and "model" accepted
 * as "assistant".
 *
 * Renders byte-for-byte what the stock template renders for string content.
 * After changing it or the model, run scripts/check_qwen_chat_template.py.
 */
internal object QwenChatTemplate {
    val TEMPLATE = """
{%- macro contentText(content) -%}
    {%- if content is string -%}
        {{- content -}}
    {%- elif content is iterable -%}
        {%- for part in content -%}
            {%- if part is string -%}
                {{- part -}}
            {%- elif part.type == 'text' -%}
                {{- part.text -}}
            {%- endif -%}
        {%- endfor -%}
    {%- endif -%}
{%- endmacro -%}
{%- if tools %}
    {{- '<|im_start|>system\n' }}
    {%- if messages[0]['role'] == 'system' %}
        {{- contentText(messages[0]['content']) }}
    {%- else %}
        {{- 'You are Qwen, created by Alibaba Cloud. You are a helpful assistant.' }}
    {%- endif %}
    {{- "\n\n# Tools\n\nYou may call one or more functions to assist with the user query.\n\nYou are provided with function signatures within <tools></tools> XML tags:\n<tools>" }}
    {%- for tool in tools %}
        {{- "\n" }}
        {{- tool | tojson }}
    {%- endfor %}
    {{- "\n</tools>\n\nFor each function call, return a json object with function name and arguments within <tool_call></tool_call> XML tags:\n<tool_call>\n{\"name\": <function-name>, \"arguments\": <args-json-object>}\n</tool_call><|im_end|>\n" }}
{%- else %}
    {%- if messages[0]['role'] == 'system' %}
        {{- '<|im_start|>system\n' }}
        {{- contentText(messages[0]['content']) }}
        {{- '<|im_end|>\n' }}
    {%- else %}
        {{- '<|im_start|>system\nYou are Qwen, created by Alibaba Cloud. You are a helpful assistant.<|im_end|>\n' }}
    {%- endif %}
{%- endif %}
{%- for message in messages %}
    {%- set role = 'assistant' if message.role == 'model' else message.role %}
    {%- if (role == "user") or (role == "system" and not loop.first) or (role == "assistant" and not message.tool_calls) %}
        {{- '<|im_start|>' + role + '\n' }}
        {{- contentText(message.content) }}
        {{- '<|im_end|>' + '\n' }}
    {%- elif role == "assistant" %}
        {{- '<|im_start|>' + role }}
        {%- set text = contentText(message.content) %}
        {%- if text %}
            {{- '\n' }}
            {{- text }}
        {%- endif %}
        {%- for tool_call in message.tool_calls %}
            {%- if tool_call.function is defined %}
                {%- set tool_call = tool_call.function %}
            {%- endif %}
            {{- '\n<tool_call>\n{"name": "' }}
            {{- tool_call.name }}
            {{- '", "arguments": ' }}
            {{- tool_call.arguments | tojson }}
            {{- '}\n</tool_call>' }}
        {%- endfor %}
        {{- '<|im_end|>\n' }}
    {%- elif role == "tool" %}
        {%- if (loop.index0 == 0) or (messages[loop.index0 - 1].role != "tool") %}
            {{- '<|im_start|>user' }}
        {%- endif %}
        {{- '\n<tool_response>\n' }}
        {{- contentText(message.content) }}
        {{- '\n</tool_response>' }}
        {%- if loop.last or (messages[loop.index0 + 1].role != "tool") %}
            {{- '<|im_end|>\n' }}
        {%- endif %}
    {%- endif %}
{%- endfor %}
{%- if add_generation_prompt %}
    {{- '<|im_start|>assistant\n' }}
{%- endif %}
""".trimStart()
}
