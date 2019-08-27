/**
 *    Copyright 2019 MetaRing s.r.l.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.metaring.framework.ext.spring.boot.rpc;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.metaring.framework.Core;
import com.metaring.framework.ext.spring.boot.MetaRingSpringBootApplication;
import com.metaring.framework.util.StringUtil;

@Configuration
@EnableWebSocket
class MetaRingSpringBootRPCConfiguration implements WebSocketConfigurer {

    protected static final String CFG_WEB_SERVICES = "webServices";
    protected static final String CFG_WEB_SERVICES_ACCESS_POINT_NAME = "accessPointName";
    protected static final String CFG_WEB_SERVICES_SOCKJS = "sockjs";

    private final MetaRingSpringBootSockJSHandler metaRingSpringBootSockJSHandler;

    public MetaRingSpringBootRPCConfiguration(MetaRingSpringBootSockJSHandler metaRingSpringBootSockJSHandler) {
        this.metaRingSpringBootSockJSHandler = metaRingSpringBootSockJSHandler;
    }

    @Override
    public final void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        boolean sockjs = true;
        try {
            sockjs = Core.SYSKB.get(MetaRingSpringBootApplication.CFG_EXT).get(MetaRingSpringBootApplication.CFG_SPRING).get(CFG_WEB_SERVICES).getTruth(CFG_WEB_SERVICES_SOCKJS);
        } catch(Exception e) {
        }
        if(!sockjs) {
            return;
        }
        String path = "call";
        try {
            path = Core.SYSKB.get(MetaRingSpringBootApplication.CFG_EXT).get(MetaRingSpringBootApplication.CFG_SPRING).get(CFG_WEB_SERVICES).getText(CFG_WEB_SERVICES_ACCESS_POINT_NAME).trim();
        } catch(Exception e) {
        }
        if(StringUtil.isNullOrEmpty(path)) {
            return;
        }
        if(!path.startsWith("/")) {
            path = "/" + path;
        }
        if(!path.endsWith(".sock")) {
            path += ".sock";
        }
        registry.addHandler(metaRingSpringBootSockJSHandler, path).setAllowedOrigins("*").withSockJS();
    }
}