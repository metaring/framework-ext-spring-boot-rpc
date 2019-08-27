/**
 * Copyright 2019 MetaRing s.r.l.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.metaring.framework.ext.spring.boot.rpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.metaring.framework.broadcast.BroadcastController;
import com.metaring.framework.broadcast.BroadcastControllerManager;
import com.metaring.framework.rpc.CallFunctionalityImpl;

@Component
public class MetaRingSpringBootSockJSHandler extends AbstractWebSocketHandler implements DisposableBean {

    private static final List<MetaRingSpringBootSockJSHandler> CONTROLLERS = new ArrayList<>();

    private final Map<String, WebSocketSession> sessions = new HashMap<>();

    private static final String BROADCAST_KEY = BroadcastController.BROADCAST_KEY;

    private static final void newController(MetaRingSpringBootSockJSHandler controller) {
        synchronized (CONTROLLERS) {
            CONTROLLERS.add(controller);
        }
    }

    private static final void controllerDisposed(MetaRingSpringBootSockJSHandler controller) {
        synchronized (CONTROLLERS) {
            CONTROLLERS.remove(controller);
            controller.sessions.values().forEach(it -> {
                try {
                    it.close(CloseStatus.GOING_AWAY);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static final void forAllControllers(final Consumer<WebSocketSession> action) {
        synchronized (CONTROLLERS) {
            CONTROLLERS.forEach(controller -> controller.sessions.values().forEach(action::accept));
        }
    }

    public static final void broadcast(final String message) {
        final TextMessage txt = new TextMessage(message);
        final Consumer<WebSocketSession> action = session -> {
            try {
                session.sendMessage(txt);
            } catch (Exception e) {
                try {
                    session.close(CloseStatus.BAD_DATA);
                } catch (Exception e1) {
                }
            }
        };
        forAllControllers(action);
    }

    private Executor MetaRingAsyncExecutor;

    public MetaRingSpringBootSockJSHandler(Executor MetaRingAsyncExecutor) {
        super();
        this.MetaRingAsyncExecutor = MetaRingAsyncExecutor;
        newController(this);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        BroadcastControllerManager.INSTANCE.thenAcceptAsync(
                it -> it.register(session.getId(), (message, error) -> respond(session, message, error)),
                MetaRingAsyncExecutor);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        BroadcastControllerManager.INSTANCE.thenAcceptAsync(it -> it.unregister(session.getId()),
                MetaRingAsyncExecutor);
    }

    @Override
    protected void handleTextMessage(final WebSocketSession session, final TextMessage message) throws IOException {
        CallFunctionalityImpl
                .execute((long) (System.currentTimeMillis() * Math.random()),
                        session.getRemoteAddress().getAddress().getHostAddress(), BROADCAST_KEY, session.getId(), false,
                        message.getPayload())
                .whenCompleteAsync((result, error) -> respond(session, result == null ? "" : result.getResult().toJson(), error),
                        MetaRingAsyncExecutor);
    }

    public final void respond(WebSocketSession session, String message, Throwable t) {
        if (t != null) {
            t.printStackTrace();
            return;
        }
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void destroy() throws Exception {
        controllerDisposed(this);
    }
}