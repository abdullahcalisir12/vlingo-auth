// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.auth.infra.resource;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import io.vlingo.actors.Actor;
import io.vlingo.actors.testkit.TestUntil;
import io.vlingo.http.Response;
import io.vlingo.http.ResponseParser;
import io.vlingo.wire.channel.ResponseChannelConsumer;
import io.vlingo.wire.message.ConsumerByteBuffer;

public class TestResponseChannelConsumer extends Actor implements ResponseChannelConsumer {
  private ResponseParser parser;
  private final Progress progress;

  public TestResponseChannelConsumer(final Progress progress) {
    this.progress = progress;
  }

  @Override
  public void consume(final ConsumerByteBuffer buffer) {
    if (parser == null) {
      parser = ResponseParser.parserFor(buffer.asByteBuffer());
    } else {
      parser.parseNext(buffer.asByteBuffer());
    }
    buffer.release();
    while (parser.hasFullResponse()) {
      final Response response = parser.fullResponse();
      synchronized (progress) {
        progress.responses.add(response);
        progress.consumeCount.incrementAndGet();
        if (progress.untilConsumed != null) {
          progress.untilConsumed.happened();
        }
      }
    }
  }

  public static class Progress {
    private TestUntil untilConsumed;
    private Queue<Response> responses = new ConcurrentLinkedQueue<>();
    private AtomicInteger consumeCount = new AtomicInteger(0);

    public Progress(final int times) {
      untilConsumed = TestUntil.happenings(times);
    }

    public void completes() {
      synchronized (this) {
        untilConsumed.completes();
      }
    }

    public int consumeCount() {
      synchronized (this) {
        return consumeCount.get();
      }
    }

    public int remaining() {
      synchronized (this) {
        return untilConsumed.remaining();
      }
    }

    public void resetTimes(final int times) {
      synchronized (this) {
        untilConsumed = TestUntil.happenings(times);
      }
    }

    public Queue<Response> responses() {
      synchronized (this) {
        return responses;
      }
    }
  }
}
