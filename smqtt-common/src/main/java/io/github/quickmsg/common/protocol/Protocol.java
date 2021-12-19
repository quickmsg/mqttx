package io.github.quickmsg.common.protocol;

import io.github.quickmsg.common.ack.Ack;
import io.github.quickmsg.common.ack.RetryAck;
import io.github.quickmsg.common.channel.MqttChannel;
import io.github.quickmsg.common.context.ContextHolder;
import io.github.quickmsg.common.event.Event;
import io.github.quickmsg.common.event.acceptor.CommonEvent;
import io.github.quickmsg.common.message.Message;
import io.github.quickmsg.common.message.SmqttMessage;
import io.github.quickmsg.common.message.mqtt.RetryMessage;
import io.netty.handler.codec.mqtt.MqttMessage;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * @author luxurong
 */
public interface Protocol<T extends Message> {


    /**
     * 解析协议添加上下文
     *
     * @param message     {@link SmqttMessage}
     * @param mqttChannel {@link MqttChannel}
     * @return Mono
     * @see MqttMessage
     */
    default Mono<Event> doParseProtocol(T message, MqttChannel mqttChannel) {
        return Mono.deferContextual(contextView -> this.parseProtocol(message, mqttChannel, contextView));
    }


    /**
     * 处理协议
     *
     * @param message     {@link T extends Message}
     * @param mqttChannel {@link MqttChannel}
     * @param contextView {@link ContextView}
     * @return Mono
     * @see MqttMessage
     */
    Mono<Event> parseProtocol(T message, MqttChannel mqttChannel, ContextView contextView);


    /**
     * @return Class
     */
    Class<T> getClassType();


    default Event build(String type, String clientId, int id) {
        return new CommonEvent(type, clientId, id, System.currentTimeMillis());
    }

    default void doRetry(long id, int retrySize, RetryMessage retrymessage) {
        RetryAck retryAck = new RetryAck(id, retrySize, 5, () -> {
            ContextHolder.getReceiveContext().getProtocolAdaptor().chooseProtocol(retrymessage);
        }, ContextHolder.getReceiveContext().getAckManager());
        retryAck.start();
    }

    default Ack getAck(int messageId, int channelId) {
        long id = (long) channelId << 4 | messageId;
        return ContextHolder.getReceiveContext().getAckManager().getAck(id);
    }

}
