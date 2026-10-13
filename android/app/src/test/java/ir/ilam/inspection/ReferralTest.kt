package ir.ilam.inspection

import ir.ilam.inspection.data.model.Referral
import ir.ilam.inspection.data.model.ReferralOutcome
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferralTest {

    /** The shape `GET /referrals` sends (FieldReferrals::publicItem on the server). */
    private val json = JSONObject(
        """
        {"id":"5d5a61ce-395e-4a6a-9968-50d406844f64","kind":1,"kind_name":"گزارش رمزارز",
         "tracking_code":"RZ-1404-000003","status":2,"status_name":"ارجاع‌شده","priority":0,
         "created_at":1760000000000,"due_at":4102444800000,"updated_at":1760000100000,
         "address":"ایلام، بلوار آزمون","plate":null,"latitude":33.64,"longitude":46.43,"accuracy":5,
         "description":"صدای فن","reporter":null,
         "details":[["نشانه‌ها","صدای مداوم فن"],["",""]],
         "photos":[{"id":"aaaaaaaa-1111-2222-3333-444444444444","captured_at":1760000000000}],
         "report_id":null,"result":null}
        """
    )

    @Test
    fun parsesWhatTheServerSends() {
        val r = Referral.fromJson(json)
        assertEquals("RZ-1404-000003", r.trackingCode)
        assertTrue(r.isCrypto)
        assertNull(r.plate)
        assertNull(r.reporter)
        assertEquals(33.64, r.latitude!!, 1e-9)
        assertEquals(listOf("نشانه‌ها" to "صدای مداوم فن"), r.details)
        assertEquals(1, r.photos.size)
        assertNull(r.resultOutcome)
        assertTrue(r.isOpen)
        assertFalse(r.isOverdue(now = 1760000000000))
        assertTrue(r.isOverdue(now = 4102444800001))
    }

    @Test
    fun aResultWaitingOnThePhoneClosesItHere() {
        val r = Referral.fromJson(json, pendingOutcome = "no_access")
        assertFalse(r.isOpen)
        val done = Referral.fromJson(JSONObject(json.toString()).put("status", 3).put("result", JSONObject().put("outcome", "case")))
        assertEquals("case", done.resultOutcome)
        assertFalse(done.isOverdue(now = Long.MAX_VALUE))
    }

    @Test
    fun onlyOurQrIsAReferral() {
        assertEquals("5d5a61ce-395e-4a6a-9968-50d406844f64", Referral.idFromQr("TKV:F1:5D5A61CE-395E-4A6A-9968-50D406844F64"))
        assertEquals("5d5a61ce-395e-4a6a-9968-50d406844f64", Referral.idFromQr(" TKV:F1:5d5a61ce-395e-4a6a-9968-50d406844f64\n"))
        assertNull(Referral.idFromQr("geo:33.64,46.43"))
        assertNull(Referral.idFromQr("TKV:F1:not-a-uuid"))
        assertNull(Referral.idFromQr("TKV:F2:5d5a61ce-395e-4a6a-9968-50d406844f64"))
        assertNull(Referral.idFromQr(null))
    }

    @Test
    fun outcomeKeysMatchTheServer() {
        // FieldReferrals::OUTCOMES on the server.
        assertEquals(listOf("confirmed", "not_found", "no_access", "other"), ReferralOutcome.entries.map { it.key })
        assertEquals(ReferralOutcome.NO_ACCESS, ReferralOutcome.of("no_access"))
        assertNull(ReferralOutcome.of("case"))
    }
}
