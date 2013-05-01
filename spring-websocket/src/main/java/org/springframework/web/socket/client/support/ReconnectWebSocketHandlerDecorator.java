/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.socket.client.support;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.support.WebSocketHandlerDecorator;


/**
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public class ReconnectWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

	private final static Log logger = LogFactory.getLog(ReconnectWebSocketHandlerDecorator.class);

	private final Runnable reconnectCallback;

	private TaskScheduler taskScheduler;

	private AtomicBoolean reconnect = new AtomicBoolean(false);


	public ReconnectWebSocketHandlerDecorator(WebSocketHandler delegate,
			Runnable reconnectCallback, TaskScheduler scheduler) {

		super(delegate);
		this.reconnectCallback = reconnectCallback;
		this.taskScheduler = scheduler;
	}


	protected AtomicBoolean getReconnect() {
		return this.reconnect;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		this.reconnect.set(false);
		super.afterConnectionEstablished(session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
		updateReconnect(closeStatus);
		scheduleReconnectTask();
		super.afterConnectionClosed(session, closeStatus);
	}

	private void updateReconnect(CloseStatus closeStatus) {
		if (CloseStatus.SERVER_ERROR.equalsCode(closeStatus) ||
				CloseStatus.SERVICE_RESTARTED.equalsCode(closeStatus) ||
				CloseStatus.NO_CLOSE_FRAME.equalsCode(closeStatus) ||
				CloseStatus.NO_STATUS_CODE.equalsCode(closeStatus)) {
			this.reconnect.set(true);
		}
	}


	private void scheduleReconnectTask() {
		logger.debug("Scheduling retry in 10 seconds");
		this.taskScheduler.schedule(RECONNECT_TASK, new Date(System.currentTimeMillis() + 10*1000));
	}


	private final Runnable RECONNECT_TASK = new Runnable() {

		@Override
		public void run() {
			if (!reconnect.get()) {
				if (logger.isTraceEnabled()) {
					logger.debug("Reconnect established, no need to retry");
				}
				return;
			}
			logger.debug("Trying to reconnect");
			try {
				reconnectCallback.run();
			}
			catch (Throwable t) {
				if (logger.isInfoEnabled()) {
					logger.info("Unable to reconnect" + t.getMessage());
				}
			}
			scheduleReconnectTask();
		}

	};

}
