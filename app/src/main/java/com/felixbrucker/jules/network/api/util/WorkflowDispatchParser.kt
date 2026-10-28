package com.felixbrucker.jules.network.api.util

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.felixbrucker.jules.network.api.model.WorkflowInput
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

data class WorkflowDispatchInfo(
    val hasWorkflowDispatch: Boolean,
    val inputs: List<WorkflowInput>
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object StringCoercingSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringCoercing", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String? {
        return if (decoder is YamlInput) {
            when (val node = decoder.node) {
                is YamlScalar -> node.content
                is YamlNull -> null
                else -> null
            }
        } else {
            try {
                decoder.decodeString()
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Serializable
private data class WorkflowInputYaml(
    val description: String? = null,
    val required: Boolean = false,
    @Serializable(with = StringCoercingSerializer::class)
    val default: String? = null,
    val type: String? = null,
    val options: List<String>? = null
)

object WorkflowDispatchParser {

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false
        )
    )

    fun parseWorkflowDispatch(yamlContent: String): WorkflowDispatchInfo {
        if (!yamlContent.contains("workflow_dispatch")) {
            return WorkflowDispatchInfo(hasWorkflowDispatch = false, inputs = emptyList())
        }

        return try {
            val rootNode = yaml.parseToYamlNode(yamlContent) as? YamlMap
                ?: return WorkflowDispatchInfo(hasWorkflowDispatch = false, inputs = emptyList())

            val onNode = rootNode.get<YamlNode>("on")
                ?: return WorkflowDispatchInfo(hasWorkflowDispatch = false, inputs = emptyList())

            when (onNode) {
                is YamlScalar -> {
                    val hasTrigger = onNode.content == "workflow_dispatch"
                    WorkflowDispatchInfo(hasWorkflowDispatch = hasTrigger, inputs = emptyList())
                }
                is YamlList -> {
                    val hasTrigger = onNode.items.any { item ->
                        item is YamlScalar && item.content == "workflow_dispatch"
                    }
                    WorkflowDispatchInfo(hasWorkflowDispatch = hasTrigger, inputs = emptyList())
                }
                is YamlMap -> {
                    val dispatchNode = onNode.get<YamlNode>("workflow_dispatch")
                        ?: return WorkflowDispatchInfo(hasWorkflowDispatch = false, inputs = emptyList())

                    val inputsList = mutableListOf<WorkflowInput>()
                    if (dispatchNode is YamlMap) {
                        val inputsNode = dispatchNode.get<YamlMap>("inputs")
                        if (inputsNode != null) {
                            val mapSerializer = MapSerializer(String.serializer(), WorkflowInputYaml.serializer())
                            val parsedInputs = yaml.decodeFromYamlNode(mapSerializer, inputsNode)
                            parsedInputs.forEach { (name, inputYaml) ->
                                inputsList.add(
                                    WorkflowInput(
                                        name = name,
                                        description = inputYaml.description,
                                        required = inputYaml.required,
                                        default = inputYaml.default,
                                        type = inputYaml.type,
                                        options = inputYaml.options
                                    )
                                )
                            }
                        }
                    }
                    WorkflowDispatchInfo(hasWorkflowDispatch = true, inputs = inputsList)
                }
                else -> WorkflowDispatchInfo(hasWorkflowDispatch = false, inputs = emptyList())
            }
        } catch (e: Exception) {
            WorkflowDispatchInfo(hasWorkflowDispatch = false, inputs = emptyList())
        }
    }
}
