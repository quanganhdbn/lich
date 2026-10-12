package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TripRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun unauthenticatedUser_cannotReadTrips() = runTest {
    auth.signOut()
    try {
      firestore.collection("users").document("someone").collection("trips").get().await()
      fail("Expected FirebaseFirestoreException for unauthenticated access")
    } catch (e: FirebaseFirestoreException) {
      assertTrue(e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
  }

  @Test
  fun authenticatedUser_canCreateAndReadOwnTrip() = runTest {
    val uid = signInTestUser("user1@example.com")

    val tripData = hashMapOf(
      "id" to "101",
      "userId" to uid,
      "tripDate" to "2026-10-12",
      "startTime" to "08:00",
      "startLocation" to "Keangnam Landmark 72",
      "destination" to "Noi Bai Airport",
      "status" to "SCHEDULED"
    )

    val docRef = firestore.collection("users").document(uid).collection("trips").document("101")
    docRef.set(tripData).await()

    val snapshot = docRef.get().await()
    assertTrue(snapshot.exists())
    assertNotNull(snapshot.getString("startLocation"))
  }

  @Test
  fun authenticatedUser_cannotAccessOtherUserTrips() = runTest {
    val user1Uid = signInTestUser("user1@example.com")
    val tripData = hashMapOf(
      "id" to "102",
      "userId" to user1Uid,
      "tripDate" to "2026-10-12",
      "startTime" to "09:00",
      "startLocation" to "Hanoi",
      "destination" to "Bac Ninh",
      "status" to "SCHEDULED"
    )
    firestore.collection("users").document(user1Uid).collection("trips").document("102").set(tripData).await()

    // Sign in as user 2
    signInTestUser("user2@example.com")

    try {
      firestore.collection("users").document(user1Uid).collection("trips").document("102").get().await()
      fail("Expected PERMISSION_DENIED when accessing another user's trips")
    } catch (e: FirebaseFirestoreException) {
      assertTrue(e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
  }
}
