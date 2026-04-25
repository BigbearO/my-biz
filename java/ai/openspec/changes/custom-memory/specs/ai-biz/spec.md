# AI-Biz: 自定义 ChatMemory 分层存储（Redis List + MySQL + Mybatis-Plus）

## ADDED Requirements

### Requirement: CustomChatMemory 直接实现 ChatMemory 接口
The system SHALL provide a `CustomChatMemory` class that directly implements `org.springframework.ai.chat.memory.ChatMemory` interface, managing sliding window logic internally without relying on `ChatMemoryRepository` or `MessageWindowChatMemory`.

#### Scenario: Direct ChatMemory implementation
- **GIVEN** Spring AI 1.1.4 `ChatMemory` interface with `add`, `get`, `clear` methods
- **WHEN** `CustomChatMemory` is instantiated
- **THEN** it SHALL implement `ChatMemory` directly
- **AND** it SHALL manage sliding window logic internally
- **AND** it SHALL coordinate Redis and MySQL storage layers

#### Scenario: Read from Redis first, fallback to MySQL, then backfill Redis
- **GIVEN** a conversation with messages stored in Redis
- **WHEN** `CustomChatMemory.get` is called
- **THEN** the system SHALL first query Redis List
- **AND** if Redis has no data, the system SHALL query MySQL as fallback
- **AND** if MySQL returns data, the system SHALL backfill Redis with the retrieved messages

#### Scenario: Write to MySQL first, then Redis
- **GIVEN** a new message to save
- **WHEN** `CustomChatMemory.add` is called
- **THEN** the system SHALL first insert each message into MySQL
- **AND** then append each message to Redis List with LTRIM
- **AND** if MySQL insert fails, Redis SHALL NOT be called

#### Scenario: Clear memory only affects Redis
- **GIVEN** a conversation with messages in both Redis and MySQL
- **WHEN** `CustomChatMemory.clear` is called
- **THEN** the system SHALL only delete the Redis key
- **AND** MySQL records SHALL remain untouched

### Requirement: RedisChatMemoryStore 基于 List 的短期存储
The system SHALL provide a `RedisChatMemoryStore` that uses Redis List to store conversation context, with one message per List element.

#### Scenario: Store messages as Redis List elements
- **GIVEN** a conversation with multiple messages to save
- **WHEN** `RedisChatMemoryStore.saveMessages` is called
- **THEN** each message SHALL be stored as a separate element in a Redis List (not as a single JSON string)

#### Scenario: Use RPUSH to append messages
- **GIVEN** a Redis List with existing messages
- **WHEN** adding a new message via `addMessage`
- **THEN** the system SHALL use `RPUSH` to append the new message to the end of the list
- **AND** each message SHALL be a separate List element

#### Scenario: Use LTRIM for sliding window
- **GIVEN** `maxMessages` is configured to 10
- **AND** a Redis List contains 12 messages
- **WHEN** trimming the list via `addMessage`
- **THEN** the system SHALL use `LTRIM key -10 -1` to keep only the last 10 messages (Redis native command)

#### Scenario: Use LRANGE to read messages
- **GIVEN** a Redis List with 10 messages
- **WHEN** reading the conversation context via `getMessages`
- **THEN** the system SHALL use `LRANGE key 0 -1` to read all messages efficiently

#### Scenario: Redis List lightweight storage
- **GIVEN** a message with content "Hello" and role "user"
- **WHEN** serializing for Redis List storage
- **THEN** the system SHALL store as lightweight JSON: `{"role":"user","content":"Hello"}`
- **AND NOT** store complete Message JSON with `@class` type information

#### Scenario: Redis List expiration
- **GIVEN** a Redis List with TTL configured to 7 days
- **WHEN** the TTL expires without activity
- **THEN** the entire list SHALL be automatically removed by Redis

### Requirement: MySQLChatMemoryStore 基于 Mybatis-Plus 的长期存储
The system SHALL provide a `MySQLChatMemoryStore` using Mybatis-Plus for persistent message storage.

#### Scenario: Save every message as separate record
- **GIVEN** a user sends a message and receives an AI response
- **WHEN** the conversation completes
- **THEN** each message SHALL be inserted as a separate row in the `message_history` table (INSERT, not UPDATE)

#### Scenario: Query from MySQL as fallback
- **GIVEN** a conversation with no data in Redis
- **WHEN** querying conversation history from MySQL via `findRecentMessages`
- **THEN** the system SHALL return messages ordered by `create_time` ascending
- **AND** limit the results to the configured `maxMessages`

#### Scenario: MySQL does not delete on clear
- **GIVEN** a conversation with messages in MySQL
- **WHEN** clearing chat memory
- **THEN** MySQL records SHALL NOT be deleted
- **AND** the full history SHALL remain accessible

#### Scenario: Message type detection and storage
- **GIVEN** messages of types `UserMessage`, `AssistantMessage`, and `SystemMessage`
- **WHEN** saving to MySQL
- **THEN** `UserMessage` SHALL be stored with `type=1` and `role="user"`
- **AND** `AssistantMessage` SHALL be stored with `type=2` and `role="assistant"`
- **AND** `SystemMessage` SHALL be stored with `type=0` and `role="system"`

### Requirement: MessageHistoryEntity 结构化存储
The system SHALL store messages using `MessageHistoryEntity` with structured fields (not JSON serialization).

#### Scenario: Structured storage per message
- **GIVEN** a message with content and role
- **WHEN** saving to MySQL
- **THEN** the system SHALL store `role` in a separate column (VARCHAR)
- **AND** store `content` in a separate column (TEXT)
- **AND** store `type` in a separate column (INTEGER)
- **AND** store `chat_id` in a separate column (VARCHAR)
- **AND** store `create_time` in a separate column (DATETIME)
- **AND NOT** serialize the entire Message object as JSON

#### Scenario: Query recent N messages by conversationId
- **GIVEN** a conversation with 100 messages in MySQL
- **WHEN** querying the recent 10 messages
- **THEN** the system SHALL use: `SELECT * FROM message_history WHERE chat_id = ? ORDER BY create_time ASC LIMIT 10`

### Requirement: 滑动窗口逻辑仅在 Redis 层处理
The system SHALL handle sliding window logic exclusively in the Redis layer.

#### Scenario: Window size enforced by LTRIM
- **GIVEN** `maxMessages` is configured to 10
- **AND** a new message is being added
- **WHEN** the Redis List exceeds 10 elements
- **THEN** the system SHALL use `LTRIM key -10 -1` to keep only the latest 10 messages
- **AND** MySQL SHALL NOT perform any delete or truncate operations

#### Scenario: Clear memory does not affect window size logic
- **GIVEN** a conversation with 10 messages in Redis List
- **WHEN** the conversation is cleared via `clear`
- **THEN** the system SHALL delete the Redis key entirely
- **AND** MySQL SHALL retain all historical messages

### Requirement: Spring AI 1.1.4 适配
The system SHALL be compatible with Spring AI 1.1.4 native message types.

#### Scenario: Support UserMessage
- **GIVEN** a Spring AI `UserMessage`
- **WHEN** converting to storage format
- **THEN** the system SHALL correctly identify it as `role="user"` and `type=1`

#### Scenario: Support AssistantMessage
- **GIVEN** a Spring AI `AssistantMessage`
- **WHEN** converting to storage format
- **THEN** the system SHALL correctly identify it as `role="assistant"` and `type=2`

#### Scenario: Support SystemMessage
- **GIVEN** a Spring AI `SystemMessage`
- **WHEN** converting to storage format
- **THEN** the system SHALL correctly identify it as `role="system"` and `type=0`

### Requirement: ChatMemory 接口完整实现
The system SHALL implement all methods of the `org.springframework.ai.chat.memory.ChatMemory` interface.

#### Scenario: Implement get
- **GIVEN** a conversation ID
- **WHEN** `get` is called
- **THEN** the system SHALL return a `List<Message>` objects
- **AND** it SHALL first check Redis, then fallback to MySQL

#### Scenario: Implement add
- **GIVEN** a conversation ID and list of messages
- **WHEN** `add` is called
- **THEN** the system SHALL persist to MySQL first, then Redis
- **AND** it SHALL apply sliding window logic in Redis

#### Scenario: Implement clear
- **GIVEN** a conversation ID
- **WHEN** `clear` is called
- **THEN** the system SHALL delete only the Redis key
- **AND** MySQL records SHALL remain

### Requirement: SimpleMessage 轻量化存储
The system SHALL use `SimpleMessage` DTO for Redis List storage instead of full Message JSON.

#### Scenario: SimpleMessage structure
- **GIVEN** a message with content "Hello" and role "user"
- **WHEN** creating a `SimpleMessage`
- **THEN** the `SimpleMessage` SHALL contain exactly two fields: `role="user"` and `content="Hello"`

#### Scenario: Redis storage format
- **GIVEN** a `SimpleMessage` with `role="assistant"` and `content="How can I help?"`
- **WHEN** serializing for Redis List storage
- **THEN** the stored value SHALL be: `{"role":"assistant","content":"How can I help?"}`
- **AND NOT** contain `@class` or type metadata

#### Scenario: Deserialize SimpleMessage from Redis
- **GIVEN** a Redis List element: `{"role":"user","content":"Hi"}`
- **WHEN** deserializing
- **THEN** the system SHALL create a `SimpleMessage` with `role="user"` and `content="Hi"`

### Requirement: Configuration 支持动态配置
The system SHALL support configurable sliding window size, Redis TTL, and key prefix.

#### Scenario: Configure max-messages
- **GIVEN** `spring.ai.chat.memory.max-messages` is set to 5
- **WHEN** a message is added to a conversation with 5 existing messages
- **THEN** the system SHALL keep only the latest 5 messages in Redis List

#### Scenario: Configure redis-ttl-days
- **GIVEN** `spring.ai.chat.memory.redis-ttl-days` is set to 3
- **WHEN** a message is saved
- **THEN** the Redis key SHALL have a TTL of 3 days

#### Scenario: Configure key-prefix
- **GIVEN** `spring.ai.chat.memory.key-prefix` is set to `"custom:prefix:"`
- **WHEN** saving messages for conversation "abc123"
- **THEN** the Redis key SHALL be `"custom:prefix:abc123"`

### Requirement: Error Handling and Resilience
The system SHALL handle errors gracefully without failing the entire operation.

#### Scenario: Redis connection failure on read
- **GIVEN** Redis is unavailable
- **WHEN** `get` is called
- **THEN** the system SHALL return an empty list for Redis
- **AND** trigger MySQL fallback query
- **AND** log the error

#### Scenario: Redis connection failure on write
- **GIVEN** Redis is unavailable
- **WHEN** `add` is called
- **THEN** the system SHALL complete the MySQL inserts successfully
- **AND** log the Redis failure
- **AND NOT** throw an exception

#### Scenario: Unknown message type
- **GIVEN** a message with an unknown type is retrieved from MySQL
- **WHEN** converting to Message object
- **THEN** the system SHALL skip the message
- **AND** log a warning
- **AND** continue processing remaining messages

### Requirement: TTL 自动续期
The system SHALL refresh the Redis key TTL on every write operation.

#### Scenario: TTL refresh on addMessage
- **GIVEN** a Redis key with remaining TTL of 2 days
- **WHEN** `addMessage` is called
- **THEN** the TTL SHALL be reset to the configured value (e.g., 7 days)

#### Scenario: TTL refresh on saveMessages
- **GIVEN** a Redis key with remaining TTL of 1 day
- **WHEN** `saveMessages` is called
- **THEN** the TTL SHALL be reset to the configured value

### Requirement: 数据一致性保证
The system SHALL maintain data consistency with MySQL-first write order.

#### Scenario: Write order: MySQL before Redis
- **GIVEN** a new message to save
- **WHEN** the system processes the save
- **THEN** MySQL INSERT SHALL complete before Redis RPUSH
- **AND** if MySQL fails, Redis SHALL NOT be called

#### Scenario: Fallback on Redis failure
- **GIVEN** MySQL insert succeeded but Redis RPUSH failed
- **WHEN** `get` is called
- **THEN** the system SHALL return data from MySQL
- **AND** the data SHALL be complete

### Requirement: 空值和边界处理
The system SHALL handle null inputs and boundary conditions correctly.

#### Scenario: Null conversationId on read
- **GIVEN** null or blank as conversationId
- **WHEN** `get` is called
- **THEN** the system SHALL return an empty list

#### Scenario: Null message in list
- **GIVEN** a list containing a null message
- **WHEN** `add` processes the list
- **THEN** the system SHALL skip the null message
- **AND** continue processing remaining messages

#### Scenario: Empty message list
- **GIVEN** an empty message list
- **WHEN** `add` is called
- **THEN** the system SHALL do nothing
- **AND** not execute any Redis or MySQL operations

#### Scenario: Concurrent writes to same conversation
- **GIVEN** two concurrent `add` calls for the same conversationId
- **WHEN** both complete
- **THEN** MySQL SHALL contain all messages from both calls
- **AND** Redis SHALL contain messages from the later call (due to RPUSH + LTRIM)

## MODIFIED Requirements

### Requirement: CustomChatMemory add 方法集成 MessageWindowChatMemory 的 process 逻辑
The system SHALL synchronize `MessageWindowChatMemory.process()` logic into `CustomChatMemory.add()`, ensuring that message deduplication, system message replacement, and sliding window trimming behave identically to the Spring AI native implementation.

#### Scenario: System message replacement when new system message arrives
- **GIVEN** Redis contains messages `[SystemMessage("old"), UserMessage("hi")]` for conversation "conv1"
- **AND** `add("conv1", [SystemMessage("new"), UserMessage("hello")])` is called
- **WHEN** the system processes the add
- **THEN** the old SystemMessage SHALL be removed from the combined message list
- **AND** the new SystemMessage SHALL be retained
- **AND** the final Redis state SHALL contain `[UserMessage("hi"), SystemMessage("new"), UserMessage("hello")]`
- **AND** MySQL SHALL contain all messages including both system messages

#### Scenario: No system message replacement when no new system message
- **GIVEN** Redis contains messages `[SystemMessage("config"), UserMessage("hi")]` for conversation "conv1"
- **AND** `add("conv1", [UserMessage("hello")])` is called with no SystemMessage
- **WHEN** the system processes the add
- **THEN** the existing SystemMessage SHALL be retained
- **AND** the final message list SHALL be `[SystemMessage("config"), UserMessage("hi"), UserMessage("hello")]`

#### Scenario: Sliding window trims non-system messages first
- **GIVEN** `maxMessages` is configured to 3
- **AND** Redis contains messages `[SystemMessage("sys"), UserMessage("1"), UserMessage("2")]`
- **AND** `add("conv1", [UserMessage("3")])` is called
- **WHEN** the system processes the add
- **THEN** the total exceeds 3 messages
- **AND** `UserMessage("1")` SHALL be removed first (oldest non-system)
- **AND** the final Redis state SHALL contain `[SystemMessage("sys"), UserMessage("2"), UserMessage("3")]`

#### Scenario: Sliding window preserves system messages during trim
- **GIVEN** `maxMessages` is configured to 2
- **AND** Redis contains messages `[SystemMessage("sys"), UserMessage("1")]`
- **AND** `add("conv1", [UserMessage("2"), UserMessage("3")])` is called
- **WHEN** the system processes the add
- **THEN** SystemMessage SHALL be preserved
- **AND** the oldest non-system messages SHALL be trimmed
- **AND** the final Redis state SHALL contain `[SystemMessage("sys"), UserMessage("3")]`

#### Scenario: Message deduplication in process logic
- **GIVEN** Redis contains messages `[UserMessage("hi"), AssistantMessage("hello")]`
- **AND** `add("conv1", [UserMessage("hi")])` is called with a duplicate message
- **WHEN** the system processes the add
- **THEN** the duplicate UserMessage SHALL still be appended (no deduplication on non-system messages)
- **AND** Redis SHALL contain `[UserMessage("hi"), AssistantMessage("hello"), UserMessage("hi")]`

#### Scenario: Process logic applied before Redis save
- **GIVEN** a conversation with existing messages in Redis
- **WHEN** `add` is called with new messages
- **THEN** the system SHALL first read existing Redis messages
- **AND** apply `process()` logic to merge existing and new messages
- **AND** then save the processed result to Redis
- **AND** save all original new messages to MySQL

#### Scenario: MySQL stores all messages regardless of window trimming
- **GIVEN** `maxMessages` is configured to 2
- **AND** `add("conv1", [UserMessage("1"), UserMessage("2"), UserMessage("3")])` is called
- **WHEN** the system processes the add
- **THEN** MySQL SHALL contain all 3 UserMessages
- **AND** Redis SHALL only contain the latest 2 messages after LTRIM

#### Scenario: Null and empty validation in add with process logic
- **GIVEN** null conversationId or empty messages list
- **WHEN** `add` is called
- **THEN** the system SHALL return early without calling process logic
- **AND** no Redis or MySQL operations SHALL be performed
