# AI-Biz: ChatMemory 分层存储（Redis List + MySQL + Mybatis-Plus）

## ADDED Requirements

### Requirement: HybridChatMemoryService 分层存储服务
The system SHALL provide a hybrid ChatMemory service that coordinates Redis List and MySQL storage layers.

#### Scenario: Read from Redis first, fallback to MySQL
- **GIVEN** a conversation with messages stored in Redis
- **WHEN** HybridChatMemoryService.findByConversationId is called
- **THEN** the system SHALL first query Redis List
- **AND** if Redis has no data, the system SHALL query MySQL as fallback

#### Scenario: Write to MySQL first, then Redis
- **GIVEN** a new message to save
- **WHEN** HybridChatMemoryService.saveAll is called
- **THEN** the system SHALL first insert the message into MySQL
- **AND** then append the message to Redis List

#### Scenario: Clear memory only affects Redis
- **GIVEN** a conversation with messages in both Redis and MySQL
- **WHEN** HybridChatMemoryService.deleteByConversationId is called
- **THEN** the system SHALL only delete the Redis key
- **AND** MySQL records SHALL remain untouched

### Requirement: RedisChatMemoryRepository 基于 List 的短期存储
The system SHALL provide a Redis List-based implementation for storing sliding window conversation context.

#### Scenario: Store messages as Redis List elements
- **GIVEN** a conversation with multiple messages to save
- **WHEN** RedisChatMemoryRepository saves the conversation
- **THEN** each message SHALL be stored as a separate element in a Redis List (not as a single JSON string)

#### Scenario: Use RPUSH to append messages
- **GIVEN** a Redis List with existing messages
- **WHEN** adding a new message
- **THEN** the system SHALL use RPUSH to append the new message to the end of the list

#### Scenario: Use LTRIM for sliding window
- **GIVEN** maxMessages is configured to 10
- **AND** a Redis List contains 12 messages
- **WHEN** trimming the list
- **THEN** the system SHALL use LTRIM key -10 -1 to keep only the last 10 messages (Redis native command)

#### Scenario: Use LRANGE to read messages
- **GIVEN** a Redis List with 10 messages
- **WHEN** reading the conversation context
- **THEN** the system SHALL use LRANGE key 0 -1 to read all messages efficiently

#### Scenario: Redis List lightweight storage
- **GIVEN** a message with content "Hello" and role "user"
- **WHEN** serializing for Redis List storage
- **THEN** the system SHALL store as lightweight JSON: {"role":"user","content":"Hello"}
- **AND NOT** store complete Message JSON with @class type information

#### Scenario: Redis List expiration
- **GIVEN** a Redis List with TTL configured to 7 days
- **WHEN** the TTL expires without activity
- **THEN** the entire list SHALL be automatically removed by Redis

### Requirement: MySQLChatMemoryRepository 基于 Mybatis-Plus 的长期存储
The system SHALL provide a MySQL-based implementation using Mybatis-Plus for persistent message storage.

#### Scenario: Save every message as separate record
- **GIVEN** a user sends a message and receives an AI response
- **WHEN** the conversation completes
- **THEN** each message SHALL be inserted as a separate row in the memory table (INSERT, not UPDATE)

#### Scenario: Query from MySQL as fallback
- **GIVEN** a conversation with no data in Redis
- **WHEN** querying conversation history from MySQL
- **THEN** the system SHALL return messages ordered by create_time ascending
- **AND** limit the results to the configured maxMessages

#### Scenario: MySQL does not delete on clear
- **GIVEN** a conversation with messages in MySQL
- **WHEN** clearing chat memory
- **THEN** MySQL records SHALL NOT be deleted
- **AND** the full history SHALL remain accessible

#### Scenario: Message type detection and storage
- **GIVEN** messages of types UserMessage, AssistantMessage, and SystemMessage
- **WHEN** saving to MySQL
- **THEN** UserMessage SHALL be stored with type=1 and role="user"
- **AND** AssistantMessage SHALL be stored with type=2 and role="assistant"
- **AND** SystemMessage SHALL be stored with type=0 and role="system"

### Requirement: MessageHistoryEntity 结构化存储
The system SHALL store messages using MessageHistoryEntity with structured fields (not JSON serialization).

#### Scenario: Structured storage per message
- **GIVEN** a message with content and role
- **WHEN** saving to MySQL
- **THEN** the system SHALL store role in a separate column (VARCHAR)
- **AND** store content in a separate column (TEXT)
- **AND NOT** serialize the entire Message object as JSON

#### Scenario: Query recent N messages by conversationId
- **GIVEN** a conversation with 100 messages in MySQL
- **WHEN** querying the recent 10 messages
- **THEN** the system SHALL use: SELECT * FROM memory WHERE chat_id = ? ORDER BY create_time ASC LIMIT 10

### Requirement: 滑动窗口逻辑仅在 Redis 层处理
The system SHALL handle sliding window logic exclusively in the Redis layer.

#### Scenario: Window size enforced by LTRIM
- **GIVEN** maxMessages is configured to 10
- **AND** a new message is being added
- **WHEN** the Redis List exceeds 10 elements
- **THEN** the system SHALL use LTRIM key -10 -1 to keep only the latest 10 messages
- **AND** MySQL SHALL NOT perform any delete or truncate operations

#### Scenario: Clear memory does not affect window size logic
- **GIVEN** a conversation with 10 messages in Redis List
- **WHEN** the conversation is cleared
- **THEN** the system SHALL delete the Redis key entirely
- **AND** MySQL SHALL retain all historical messages

### Requirement: Spring AI 1.1.4 适配
The system SHALL be compatible with Spring AI 1.1.4 native message types.

#### Scenario: Support UserMessage
- **GIVEN** a Spring AI UserMessage
- **WHEN** converting to storage format
- **THEN** the system SHALL correctly identify it as role="user" and type=1

#### Scenario: Support AssistantMessage
- **GIVEN** a Spring AI AssistantMessage
- **WHEN** converting to storage format
- **THEN** the system SHALL correctly identify it as role="assistant" and type=2

#### Scenario: Support SystemMessage
- **GIVEN** a Spring AI SystemMessage
- **WHEN** converting to storage format
- **THEN** the system SHALL correctly identify it as role="system" and type=0

### Requirement: ChatMemoryRepository 接口实现
The system SHALL implement the ChatMemoryRepository interface from Spring AI.

#### Scenario: Implement findByConversationId
- **GIVEN** a conversation ID
- **WHEN** findByConversationId is called
- **THEN** the system SHALL return a List of Message objects

#### Scenario: Implement saveAll
- **GIVEN** a conversation ID and list of messages
- **WHEN** saveAll is called
- **THEN** the system SHALL persist to both MySQL and Redis

#### Scenario: Implement deleteByConversationId
- **GIVEN** a conversation ID
- **WHEN** deleteByConversationId is called
- **THEN** the system SHALL delete only the Redis key

### Requirement: SimpleMessage 轻量化存储
The system SHALL use SimpleMessage DTO for Redis List storage instead of full Message JSON.

#### Scenario: SimpleMessage structure
- **GIVEN** a message with content "Hello" and role "user"
- **WHEN** creating a SimpleMessage
- **THEN** the SimpleMessage SHALL contain exactly two fields: role="user" and content="Hello"

#### Scenario: Redis storage format
- **GIVEN** a SimpleMessage with role="assistant" and content="How can I help?"
- **WHEN** serializing for Redis List storage
- **THEN** the stored value SHALL be: {"role":"assistant","content":"How can I help?"}
- **AND NOT** contain @class or type metadata

#### Scenario: Deserialize SimpleMessage from Redis
- **GIVEN** a Redis List element: {"role":"user","content":"Hi"}
- **WHEN** deserializing
- **THEN** the system SHALL create a SimpleMessage with role="user" and content="Hi"

### Requirement: Configuration 支持动态配置
The system SHALL support configurable sliding window size, Redis TTL, and key prefix.

#### Scenario: Configure max-messages
- **GIVEN** spring.ai.chat.memory.max-messages is set to 5
- **WHEN** a message is added to a conversation with 5 existing messages
- **THEN** the system SHALL keep only the latest 5 messages in Redis List

#### Scenario: Configure redis-ttl-days
- **GIVEN** spring.ai.chat.memory.redis-ttl-days is set to 3
- **WHEN** a message is saved
- **THEN** the Redis key SHALL have a TTL of 3 days

#### Scenario: Configure key-prefix
- **GIVEN** spring.ai.chat.memory.key-prefix is set to "custom:prefix:"
- **WHEN** saving messages for conversation "abc123"
- **THEN** the Redis key SHALL be "custom:prefix:abc123"

### Requirement: Error Handling and Resilience
The system SHALL handle errors gracefully without failing the entire operation.

#### Scenario: Redis connection failure on read
- **GIVEN** Redis is unavailable
- **WHEN** findByConversationId is called
- **THEN** the system SHALL return an empty list
- **AND** log the error

#### Scenario: Redis connection failure on write
- **GIVEN** Redis is unavailable
- **WHEN** saveAll is called
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
- **WHEN** addMessage is called
- **THEN** the TTL SHALL be reset to the configured value (e.g., 7 days)

#### Scenario: TTL refresh on saveAll
- **GIVEN** a Redis key with remaining TTL of 1 day
- **WHEN** saveAll is called
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
- **WHEN** findByConversationId is called
- **THEN** the system SHALL return data from MySQL
- **AND** the data SHALL be complete

### Requirement: 空值和边界处理
The system SHALL handle null inputs and boundary conditions correctly.

#### Scenario: Null conversationId on read
- **GIVEN** null as conversationId
- **WHEN** findByConversationId is called
- **THEN** the system SHALL return an empty list

#### Scenario: Null message in list
- **GIVEN** a list containing a null message
- **WHEN** saveAll processes the list
- **THEN** the system SHALL skip the null message
- **AND** continue processing remaining messages

#### Scenario: Empty message list
- **GIVEN** an empty message list
- **WHEN** saveAll is called
- **THEN** the system SHALL do nothing
- **AND** not execute any Redis or MySQL operations

#### Scenario: Concurrent writes to same conversation
- **GIVEN** two concurrent saveAll calls for the same conversationId
- **WHEN** both complete
- **THEN** MySQL SHALL contain all messages from both calls
- **AND** Redis SHALL contain messages from the later call (due to DEL + RPUSHALL)

