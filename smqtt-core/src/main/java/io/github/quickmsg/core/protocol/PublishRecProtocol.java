package io.github.quickmsg.core.protocol;

import io.github.quickmsg.common.channel.MqttChannel;
import io.github.quickmsg.common.event.Event;
import io.github.quickmsg.common.message.mqtt.PublishRecMessage;
import io.github.quickmsg.common.protocol.Protocol;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * @author luxurong
 */
public class PublishRecProtocol implements Protocol<PublishRecMessage> {

    // todo 暂不支持qos2
    @Override
    public Mono<Event> parseProtocol(PublishRecMessage message, MqttChannel mqttChannel, ContextView contextView) {
//        int messageId = message.getMessageId();
//        return mqttChannel.cancelRetry(MqttMessageType.PUBLISH, messageId)
//                .then(mqttChannel.write(MqttMessageUtils.buildPublishRel(messageId), true))
//                .thenReturn(build(EventMsg.PUB_REC_MESSAGE,
//                        mqttChannel.getConnectMessage().getClientId(),
//                        messageId));
        return Mono.empty();
    }

    @Override
    public Class<PublishRecMessage> getClassType() {
        return PublishRecMessage.class;
    }
}
