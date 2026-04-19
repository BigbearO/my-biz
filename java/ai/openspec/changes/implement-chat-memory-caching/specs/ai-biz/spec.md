# AI-Biz: ChatMemory 分层存储架构（Redis List 版）

## ADDED Requirements

### Requirement: Redis List 短期上下文存储
The system SHALL provide a Redis List-based implementation of ChatMemoryRepository for storing sliding window conversation context.

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

#### Scenario: Read recent N messages only
- **GIVEN** a Redis List with 20 messages
- **WHEN** only the last 5 messages are needed
- **THEN** the system SHALL use LRANGE key -5 -1 to read only the required messages

#### Scenario: Redis List expiration
- **GIVEN** a Redis List with TTL configured to 7 days
- **WHEN** the TTL expires without activity
- **THEN** the entire list SHALL be automatically removed by Redis

### Requirement: PostgreSQL 长期历史存储
The system SHALL provide a ChatHistoryService that persists every message to PostgreSQL for long-term storage.

#### Scenario: Save every message as separate record
- **GIVEN** a user sends a message and receives an AI response
- **WHEN** the conversation completes
- **THEN** each message SHALL be inserted as a separate row in the memory table (INSERT, not UPDATE)

#### Scenario: Query full conversation history
- **GIVEN** a conversation with 100 messages in PostgreSQL
- **WHEN** admin queries the conversation history
- **THEN** all 100 messages SHALL be returned ordered by timestamp

#### Scenario: Message type detection and storage
- **GIVEN** messages of types UserMessage, AssistantMessage, and SystemMessage
- **WHEN** saving to PostgreSQL
- **THEN** each message SHALL be stored with correct type identifier (0=System, 1=User, 2=Assistant)

### Requirement: Message 多态序列化
The system SHALL support polymorphic serialization of Message types using Jackson for Redis List elements.

#### Scenario: Serialize each message individually
- **GIVEN** a list containing UserMessage and AssistantMessage
- **WHEN** serializing for Redis List storage
- **THEN** each message SHALL be serialized separately with @class type discriminator

#### Scenario: Deserialize message list from Redis List
- **GIVEN** a Redis List containing JSON strings with @class type information
- **WHEN** deserializing to reconstruct Message objects
- **THEN** the system SHALL correctly instantiate UserMessage, AssistantMessage, etc.

### Requirement: 滑动窗口算法优化
The system SHALL use MessageWindowChatMemory with Redis List LTRIM for efficient sliding window implementation.

#### Scenario: Efficient sliding window with LTRIM
- **GIVEN** maxMessages is configured to 10
- **AND** a conversation accumulates 15 messages
- **WHEN** MessageWindowChatMemory updates the window
- **THEN** the system SHALL use Redis LTRIM instead of Java list operations for better performance

## MODIFIED Requirements

### Requirement: 复用现有 memory 表
The system SHALL reuse the existing `memory` table (MessageHistoryEntity) for long-term message storage.

#### Scenario: Insert into existing memory table
- **GIVEN** the existing memory table with columns: id, chat_id, content, type, create_time
- **WHEN** ChatHistoryService saves a message
- **THEN** the message SHALL be inserted using the existing schema

#### Scenario: Query from existing memory table
- **GIVEN** historical data in the memory table
- **WHEN** querying conversation history
- **THEN** existing data SHALL remain accessible

## REMOVED Requirements

### Requirement: 废弃 Redis String 存储方案
**Reason**: Changed to Redis List for better performance with RPUSH/LTRIM/LRANGE operations
**Migration**: Use Redis List operations instead of String SET/GET

### Requirement: 废弃 PostgreSqlChatMemoryRepository 作为 Spring AI 存储
**Reason**: PostgreSQL stores full history independently via ChatHistoryService, not as Spring AI ChatMemory backend
**Migration**: Use ChatHistoryService for PostgreSQL operations

### Requirement: 废弃 CachingChatMemoryRepository 双层缓存装饰器
**Reason**: No longer needed - Redis List and PostgreSQL serve different purposes (short-term vs long-term)
**Migration**: Directly use RedisChatMemoryRepository for Spring AI, ChatHistoryService for PostgreSQL

### Requirement: 废弃 chat_memory 表调整
**Reason**: Reusing existing memory table instead of modifying chat_memory
**Migration**: No database migration needed for chat_memory table
