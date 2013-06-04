/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.x.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.Message;
import org.springframework.integration.MessagingException;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageHandler;
import org.springframework.integration.test.util.SocketUtils;
import org.springframework.web.client.RestTemplate;

/**
 * @author Mark Fisher
 */
public class NettyHttpInboundChannelAdapterTests {

	@Test
	public void test() throws Exception {
		final List<Message<?>> messages = new ArrayList<Message<?>>();
		final CountDownLatch latch = new CountDownLatch(2);
		DirectChannel channel = new DirectChannel();
		channel.subscribe(new MessageHandler() {
			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				messages.add(message);
				latch.countDown();
			}
		});
		int port = SocketUtils.findAvailableServerSocket();
		NettyHttpInboundChannelAdapter adapter = new NettyHttpInboundChannelAdapter(port);
		adapter.setOutputChannel(channel);
		adapter.start();
		RestTemplate template = new RestTemplate();
		URI uri1 = new URI("http://localhost:" + port + "/test1");
		URI uri2 = new URI("http://localhost:" + port + "/test2");
		ResponseEntity<?> response1 = template.postForEntity(uri1, "foo", Object.class);
		ResponseEntity<?> response2 = template.postForEntity(uri2, "bar", Object.class);
		assertEquals(HttpStatus.OK, response1.getStatusCode());
		assertEquals(HttpStatus.OK, response2.getStatusCode());
		assertTrue(latch.await(1, TimeUnit.SECONDS));
		assertEquals(2, messages.size());
		Message<?> message1 = messages.get(0);
		Message<?> message2 = messages.get(1);
		assertEquals("foo", message1.getPayload());
		assertEquals("bar", message2.getPayload());
		assertEquals("/test1", message1.getHeaders().get("requestPath"));
		assertEquals("/test2", message2.getHeaders().get("requestPath"));
	}

}