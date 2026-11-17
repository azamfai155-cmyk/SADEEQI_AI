package com.azzam.sadeeqi.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FounderIdentityTest {
    @Test fun answersFounderQuestionDeterministically() {
        assertEquals(
            "أنا صديقي، مساعد ذكي أنشأه وطوّره عزام فايز ضمن مشروع SADEEQI AI.",
            FounderIdentity.answerFor("من صنعك؟")
        )
        assertEquals("مؤسس مشروع SADEEQI AI هو عزام فايز.", FounderIdentity.answerFor("من مؤسسك؟"))
        assertEquals("صاحب فكرة مشروع صديقي هو عزام فايز.", FounderIdentity.answerFor("من صاحبك؟"))
        assertEquals("اسمي صديقي، مساعدك الذكي ضمن مشروع SADEEQI AI.", FounderIdentity.answerFor("ما اسمك؟"))
    }

    @Test fun ignoresOrdinaryConversationAsIdentityMutation() {
        assertNull(FounderIdentity.answerFor("أريد تغيير اسم المشروع إلى شيء آخر"))
    }
}
