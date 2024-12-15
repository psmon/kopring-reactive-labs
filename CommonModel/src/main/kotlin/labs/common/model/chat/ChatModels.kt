package labs.common.model.chat

data class CustomFieldRequest(
    val conversationid: String,
    val custom_fields: Map<String, String>
)

data class CustomFieldResponse(
    val data: List<CustomFieldData>,
    val errorMessage: String?,
    val message: String,
    val success: Boolean
)

data class CustomFieldData(
    val field: String,
    val id: Long
)

data class AddCustomerRequest(
    val from: String,
    val to: String,
    val channel: String,
    val userdata: UserData
)

data class UserData(
    val countryCode: String,
    val mobilenumber: String,
    val firstName: String,
    val lastName: String
)

data class AddCustomerResponse(
    val message: String,
    val success: Boolean
)

data class AddTagRequest(
    val conversationid: String,
    val tag: String
)

data class AddTagResponse(
    val errorMessage: String?,
    val success: Boolean
)

data class FetchChatsResponse(
    val cursor: String?,
    val errorMessage: String?,
    val messages: List<ChatMessage>,
    val success: Boolean
)

data class ChatMessage(
    val agent: Agent?,
    val contextproduct: Any?,
    val conversation_active: Boolean,
    val conversationid: String,
    val created: String,
    val delivered: Boolean,
    val fail_reason: String?,
    val message: String,
    val messageid: String,
    val mobile_template: List<Any>?,
    val negative: String?,
    val page_conversation_id: String?,
    val pageid: String?,
    val platform: String?,
    val positive: String?,
    val reaction: String?,
    val replied: Boolean,
    val replytomessage: Any?,
    val rule: String?,
    val schedule: Any?,
    val shop: String?,
    val status: String?,
    val stuckerror: Boolean?,
    val template: Any?,
    val to_agent: Any?,
    val vertex: String?,
    val more: Boolean
)

data class Agent(
    val agent_nickname: String?,
    val email: String?,
    val firstName: String?,
    val id: String?,
    val lastName: String?,
    val mobile: String?,
    val name: String?,
    val picture: String?
)

data class FetchConversationsResponse(
    val count: Int,
    val data: List<Conversation>,
    val errorMessage: String?,
    val next: String?,
    val prev: Any?,
    val success: Boolean
)

data class Conversation(
    val active: Boolean,
    val assigned_to: Agent?,
    val channel: String,
    val conversationid: String,
    val email: String?,
    val image: String?,
    val lastReplyTimestamp: String,
    val name: String,
    val outletid: String
)

data class FetchTagsResponse(
    val data: List<Any>,
    val errorMessage: String?,
    val success: Boolean
)

data class CustomField(
    val field: String,
    val id: Long,
    val value: String
)

data class FetchKakaoAppTemplatesResponse(
    val errorMessage: String?,
    val prev: String?,
    val next: String?,
    val data: List<KakaoAppTemplate>,
    val success: Boolean
)

data class KakaoAppTemplate(
    val category: String,
    val template_name: String,
    val templates: List<Any>
)

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

data class GetMessageResponse(
    val message: String?,
    val errorMessage: String?,
    val data: MessageData?,
    val success: Boolean
)

data class MessageData(
    val status: String,
    val delivered: Boolean,
    val last_updated: String,
    val to_agent: String?,
    val sent_time: String,
    val from_bot: Boolean,
    val schedule: String?,
    val fail_reason: String?,
    val agent: Agent?,
    val page_conversation_id: String,
    val conversationid: String,
    val platform: String,
    val reaction: String?,
    val template: Template?,
    val messageid: String,
    val pageid: String,
    val message: String,
    val replytomessage: String?
)

data class Template(
    val message: String,
    val type: String
)

data class GetSocialChannelsResponse(
    val data: List<SocialChannel>,
    val errorMessage: String?,
    val success: Boolean
)

data class SocialChannel(
    val field: String,
    val id: Long,
    val value: String
)

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

data class GetKakaoAppTemplateResponse(
    val errorMessage: String?,
    val data: List<KakaoTemplateInfo>,
    val success: Boolean
)

data class KakaoTemplateInfo(
    val category: String,
    val status: String,
    val product: String?,
    val message_params: MessageParams,
    val language: String,
    val template_name: String,
    val messageinfo: MessageInfo,
    val rejected_reason: String?
)

data class MessageParams(
    val BODY: Any?,
    val HEADER: Any?
)

data class MessageInfo(
    val wa_template_name: String,
    val replace_params_key: Any?,
    val wa_id: String,
    val template_id: String,
    val collection_id: String,
    val message: Any?,
    val id: String
)
