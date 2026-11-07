package dev.nothingtype.swipe;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public class SwipeDecoderTest {
    @Test public void ranksTraceWithRepeatedKeyAndRejectsDistantWords() {
        assertEquals(List.of("hello", "help"), SwipeDecoder.decode(
                "hhheelllo", List.of("help", "hello", "calendar"), 3));
    }

    @Test public void supportsRussianAndAnEmptyTrace() {
        assertEquals(List.of("привет"), SwipeDecoder.decode("ппривеет", List.of("пока", "привет"), 1));
        assertEquals(List.of(), SwipeDecoder.decode("  ", List.of("hello"), 3));
    }
}