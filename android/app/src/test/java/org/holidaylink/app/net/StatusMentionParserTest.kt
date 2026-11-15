package org.holidaylink.app.net

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusMentionParserTest {
    @Test fun extractsHandlesAndDeduplicatesCaseInsensitively() {
        assertEquals(listOf("ezra", "friend_2"), StatusMentionParser.handles("Hi @ezra and @friend_2, again @EZRA"))
    }

    @Test fun ignoresBareAtAndPunctuationOnly() {
        assertEquals(emptyList<String>(), StatusMentionParser.handles("hello @ !!! @- @."))
    }

    @Test fun ignoresEmailsAndDoesNotCaptureMoreThanHandleCharacters() {
        assertEquals(listOf("person"), StatusMentionParser.handles("mail person@example.com then @person!"))
    }
}
