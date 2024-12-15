package org.example.kotlinbootreactivelabs.controller

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import labs.common.model.chat.AddCustomerRequest
import labs.common.model.chat.AddCustomerResponse
import labs.common.model.chat.AddTagRequest
import labs.common.model.chat.AddTagResponse
import labs.common.model.chat.Agent
import labs.common.model.chat.ChatMessage
import labs.common.model.chat.Conversation
import labs.common.model.chat.CustomField
import labs.common.model.chat.CustomFieldData
import labs.common.model.chat.CustomFieldRequest
import labs.common.model.chat.CustomFieldResponse
import labs.common.model.chat.FetchChatsResponse
import labs.common.model.chat.FetchConversationsResponse
import labs.common.model.chat.FetchTagsResponse
import labs.common.model.chat.FetchKakaoAppTemplatesResponse
import labs.common.model.chat.GetMessageResponse
import labs.common.model.chat.GetSocialChannelsResponse
import labs.common.model.chat.GetKakaoAppTemplateResponse
import labs.common.model.chat.MessageData
import labs.common.model.chat.MessageInfo
import labs.common.model.chat.MessageParams
import labs.common.model.chat.SocialChannel
import labs.common.model.chat.Template
import labs.common.model.chat.UserData
import labs.common.model.chat.KakaoAppTemplate
import labs.common.model.chat.KakaoTemplateInfo
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat Controller")
class ChatController {

    @PostMapping("/add-custom-field")
    @Operation(
        summary = "대화에 사용자 정의 필드 추가",
        description = "사용자 정의 필드는 대화와 관련된 키-값 쌍의 정보를 저장하고 검색할 수 있는 데이터 구조입니다.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "대화에 사용자 정의 필드 추가",
                content = [Content(schema = Schema(implementation = CustomFieldResponse::class))]
            )
        ]
    )
    fun addCustomField(@RequestBody request: CustomFieldRequest): CustomFieldResponse {
        // Implementation here
        val customFieldData = request.custom_fields.map { CustomFieldData(it.key, 1L) } // 예시로 ID를 1로 설정
        return CustomFieldResponse(
            data = customFieldData,
            errorMessage = null,
            message = "Custom field added",
            success = true
        )
    }

    @PostMapping("/add-customer")
    @Operation(
        summary = "모바일 번호와 페이지 ID를 사용하여 고객 추가 또는 업데이트",
        description = "모바일 번호와 페이지 ID 조합을 기본 키로 사용하여 고객을 추가하거나 업데이트합니다. 이 API는 CRM 패널에 새 고객을 추가하는 데 사용할 수 있습니다.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "고객 추가 응답",
                content = [Content(schema = Schema(implementation = AddCustomerResponse::class))]
            )
        ]
    )
    fun addCustomer(@RequestBody request: AddCustomerRequest): AddCustomerResponse {
        // Implementation here
        return AddCustomerResponse(
            message = "Customer added or updated",
            success = true
        )
    }

    @PostMapping("/add-tag")
    @Operation(
        summary = "고객 대화에 새 태그 추가",
        description = "고객 대화에 새 태그를 추가합니다. 이는 대화를 분류하는 데 유용합니다.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "대화에 태그 추가 응답",
                content = [Content(schema = Schema(implementation = AddTagResponse::class))]
            )
        ]
    )
    fun addTag(@RequestBody request: AddTagRequest): AddTagResponse {
        // Implementation here
        return AddTagResponse(
            errorMessage = null,
            success = true
        )
    }

    @GetMapping("/fetch-chats")
    @Operation(
        summary = "고객과 채널 간의 마지막 10개의 메시지 가져오기",
        description = "고객과 채널 간의 마지막 10개의 메시지를 가져옵니다. 커서를 사용하면 기록을 가져오는 데 사용할 수 있습니다.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "대화의 채팅 메시지 가져오기",
                content = [Content(schema = Schema(implementation = FetchChatsResponse::class))]
            )
        ]
    )
    fun fetchChats(@RequestParam conversationid: String): FetchChatsResponse {
        // Implementation here
        val messages = listOf<ChatMessage>() // Example empty list
        return FetchChatsResponse(
            cursor = null,
            errorMessage = null,
            messages = messages,
            success = true
        )
    }


    @GetMapping("/fetch-conversations")
    @Operation(
        summary = "채널별로 모든 대화 가져오기",
        description = "채널별로 모든 대화를 가져옵니다. 추가 매개변수를 적용하여 대화를 필터링할 수 있습니다. (예: channel=all&retarget=false&size=25)",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "대화 목록 가져오기",
                content = [Content(schema = Schema(implementation = FetchConversationsResponse::class))]
            )
        ]
    )
    fun fetchConversations(
        @RequestParam channel: String,
        @RequestParam retarget: String?,
        @RequestParam size: String?
    ): FetchConversationsResponse {
        // Implementation here
        val conversations = listOf<Conversation>() // Example empty list
        return FetchConversationsResponse(
            count = conversations.size,
            data = conversations,
            errorMessage = null,
            next = null,
            prev = null,
            success = true
        )
    }

    @GetMapping("/fetch-tags")
    @Operation(
        summary = "특정 대화와 관련된 모든 사용자 정의 필드 가져오기",
        description = "특정 대화와 관련된 모든 사용자 정의 필드를 가져옵니다.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "대화와 관련된 사용자 정의 필드 또는 태그 가져오기",
                content = [Content(schema = Schema(implementation = FetchTagsResponse::class))]
            )
        ]
    )
    fun fetchTags(@RequestParam filter_fields: String): FetchTagsResponse {
        // Implementation here
        val data = if (filter_fields == "customfield") {
            listOf(CustomField("exampleField", 1L, "exampleValue")) // Example data
        } else {
            listOf("exampleTag") // Example data
        }
        return FetchTagsResponse(
            data = data,
            errorMessage = null,
            success = true
        )
    }

    @GetMapping("/fetch-kakao-templates")
    @Operation(
        summary = "상인 계정 내에서 설계된 모든 kakao 템플릿 가져오기",
        description = "상인 계정 내에서 설계된 모든 kakao 템플릿을 가져옵니다.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "kakao 템플릿 가져오기",
                content = [Content(schema = Schema(implementation = FetchKakaoAppTemplatesResponse::class))]
            )
        ]
    )
    fun fetchKakaoAppTemplates(@RequestParam limit: Int): FetchKakaoAppTemplatesResponse {
        // Implementation here
        val templates = listOf<KakaoAppTemplate>() // Example empty list
        return FetchKakaoAppTemplatesResponse(
            errorMessage = null,
            prev = null,
            next = null,
            data = templates,
            success = true
        )
    }

    data class GetConversationResponse(
        val data: ConversationData,
        val errorMessage: String?,
        val success: Boolean
    )

    data class ConversationData(
        val active: Boolean,
        val assigned_to: Agent?,
        val channel: String,
        val consented_time: String?,
        val custom_fields: List<CustomField>?,
        val dob: String?,
        val email: String?,
        val existing: Boolean,
        val followup: Boolean,
        val gender: String?,
        val has_consented_latest: Boolean,
        val headerinfo: Any?,
        val image: String?,
        val lastReplyTimestamp: String,
        val locale: String?,
        val loyalty_points: Int?,
        val mobile: String?,
        val mobilecountrycode: String?,
        val name: String,
        val product: String?,
        val psid: String?,
        val status: String?,
        val step: String?,
        val subscribed: Boolean,
        val tags: List<String>?,
        val timezone: String?,
        val verified: Boolean,
        val vertex: Int?
    )

    @GetMapping("/get-message")
    @Operation(
        summary = "보낸 메시지 및 메시지 정보 검색",
        description = "보낸 메시지 및 메시지 정보를 검색합니다.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "대화에 대한 사용자 정의 필드 가져오기",
                content = [Content(schema = Schema(implementation = GetMessageResponse::class))]
            )
        ]
    )
    fun getMessage(@RequestParam message_id: String): GetMessageResponse {
        // Implementation here
        val messageData = MessageData(
            status = "delivered",
            delivered = true,
            last_updated = "2023-10-01T12:00:00Z",
            to_agent = "agent123",
            sent_time = "2023-10-01T12:00:00Z",
            from_bot = false,
            schedule = null,
            fail_reason = null,
            agent = null,
            page_conversation_id = "1234567890",
            conversationid = "conversation123",
            platform = "whatsapp",
            reaction = null,
            template = Template(message = "Hello", type = "text"),
            messageid = message_id,
            pageid = "page123",
            message = "Hello",
            replytomessage = null
        )
        return GetMessageResponse(
            message = "Message retrieved successfully",
            errorMessage = null,
            data = messageData,
            success = true
        )
    }

    @GetMapping("/get-social-channels")
    @Operation(
        summary = "상인의 연결된 소셜 채널 가져오기",
        description = "상인의 연결된 소셜 채널을 가져옵니다.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "상인의 연결된 소셜 채널 가져오기",
                content = [Content(schema = Schema(implementation = GetSocialChannelsResponse::class))]
            )
        ]
    )
    fun getSocialChannels(): GetSocialChannelsResponse {
        // Implementation here
        val socialChannels = listOf(
            SocialChannel(field = "Facebook", id = 1L, value = "facebook.com/merchant"),
            SocialChannel(field = "Twitter", id = 2L, value = "twitter.com/merchant")
        )
        return GetSocialChannelsResponse(
            data = socialChannels,
            errorMessage = null,
            success = true
        )
    }

    @GetMapping("/get-kakao-template")
    @Operation(
        summary = "선택한 템플릿에 대한 추가 정보 가져오기",
        description = "선택한 템플릿에 대한 추가 정보를 가져옵니다. 이 정보에는 템플릿에 존재하는 버튼 수, 템플릿에 존재하는 요소 등이 포함됩니다.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "WhatsApp 템플릿 형식 가져오기",
                content = [Content(schema = Schema(implementation = GetKakaoAppTemplateResponse::class))]
            )
        ]
    )
    fun getKakaoAppTemplate(@RequestParam id: Int): GetKakaoAppTemplateResponse {
        // Implementation here
        val templateInfo = KakaoTemplateInfo(
            category = "TRANSACTIONAL",
            status = "APPROVED",
            product = "exampleProduct",
            message_params = MessageParams(BODY = null, HEADER = null),
            language = "en",
            template_name = "exampleTemplate",
            messageinfo = MessageInfo(
                wa_template_name = "exampleTemplate",
                replace_params_key = null,
                wa_id = "wa123",
                template_id = "template123",
                collection_id = "collection123",
                message = null,
                id = "1234567890123456"
            ),
            rejected_reason = null
        )
        return GetKakaoAppTemplateResponse(
            errorMessage = null,
            data = listOf(templateInfo),
            success = true
        )
    }

    data class SendMessageRequest(
        val from: String?,
        val to: String,
        val channel: String,
        val source: String?,
        val message: String,
        val messagetype: String?,
        val message_params: Map<String, Any>?,
        val userdata: UserData?
    )

    data class SendMessageResponse(
        val message_id: String,
        val conversationid: String,
        val errorMessage: String?,
        val message: String,
        val success: Boolean
    )
}