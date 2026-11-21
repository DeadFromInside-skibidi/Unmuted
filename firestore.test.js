const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const CHARLIE_UID = "charlie_uid_003";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated requests are denied across all collections", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("posts").where("visibility", "==", "PUBLIC").get());
  await assertFails(db.collection("rooms").where("visibility", "==", "PUBLIC").get());
  await assertFails(db.collection("users").where("visibility", "==", "PUBLIC").get());
  await assertFails(db.collection("conversations").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(db.collection("audio_blobs").doc("blob_1").get());
});

test("UserProfile create, update, shadow-field rejection, and cross-user write protection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Studio",
      handle: "alice_voice",
      bio: "Late night takes",
      accentKey: "CORAL",
      bannerKey: "OBSIDIAN_WAVE",
      waveformStyle: "BARS",
      interests: ["Tech", "Music"],
      followerCount: 0,
      followingCount: 0,
      visibility: "PUBLIC",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Speaking unfiltered",
      accentKey: "VIOLET",
      updatedAt: pastDate(1000),
    })
  );

  // Shadow ghost field is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Bob cannot overwrite Alice's profile
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: pastDate(500),
    })
  );

  // Public filtered query succeeds, unfiltered query fails
  await assertSucceeds(bobDb.collection("users").where("visibility", "==", "PUBLIC").get());
  await assertFails(bobDb.collection("users").get());
});

test("AudioBlob create, read, and delete ownership enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("audio_blobs").doc("blob_1").set({
      authorUid: ALICE_UID,
      audioBase64: "AAAAIGZ0eXBpc29t",
      mimeType: "audio/mp4",
      durationMs: 4200,
      createdAt: pastDate(5000),
    })
  );

  await assertSucceeds(bobDb.collection("audio_blobs").doc("blob_1").get());
  await assertFails(bobDb.collection("audio_blobs").doc("blob_1").delete());
  await assertFails(bobDb.collection("audio_blobs").get());
  await assertSucceeds(aliceDb.collection("audio_blobs").doc("blob_1").delete());
});

test("VoicePost & Comments create, engagement counters, and anonymous mode parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      authorUid: ALICE_UID,
      authorName: "Alice Studio",
      authorHandle: "alice_voice",
      authorAccentKey: "CORAL",
      postKind: "FEED_VOICE",
      roomId: "room_anon_1",
      roomSlug: "unpopular",
      anonymousMode: true,
      anonymousAlias: "Phantom_482",
      voiceDisguise: "DEEP",
      audioMediaId: "blob_1",
      durationMs: 12000,
      waveformPeaks: [0.2, 0.7, 0.9, 0.4],
      transcript: "Honestly remote work changed everything.",
      captionSegments: [{ startMs: 0, endMs: 4000, text: "Honestly remote work changed everything." }],
      headlineText: "Hot take on office culture",
      hashtags: ["work", "unmuted"],
      likeCount: 0,
      dislikeCount: 0,
      commentCount: 0,
      echoCount: 0,
      visibility: "PUBLIC",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("posts").doc("post_future").set({
      authorUid: ALICE_UID,
      authorName: "Alice Studio",
      authorHandle: "alice_voice",
      postKind: "FEED_VOICE",
      anonymousMode: false,
      voiceDisguise: "NONE",
      durationMs: 5000,
      waveformPeaks: [0.5],
      transcript: "Future post",
      captionSegments: [],
      likeCount: 0,
      dislikeCount: 0,
      commentCount: 0,
      echoCount: 0,
      visibility: "PUBLIC",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Bob can increment likeCount (Action 2) but cannot edit transcript (Action 1)
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").update({
      likeCount: 1,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({
      transcript: "Tampered transcript",
      updatedAt: pastDate(1000),
    })
  );

  // Bob adds voice comment on existing post
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("comments").doc("c_1").set({
      postId: "post_1",
      authorUid: BOB_UID,
      authorName: "Bob",
      authorHandle: "bob_v",
      anonymousMode: true,
      anonymousAlias: "CipherOwl_74",
      voiceDisguise: "MASKED",
      textBody: "Totally agree with this take.",
      durationMs: 0,
      waveformPeaks: [],
      likeCount: 0,
      dislikeCount: 0,
      visibility: "PUBLIC",
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // Filtered queries succeed
  await assertSucceeds(bobDb.collection("posts").where("visibility", "==", "PUBLIC").get());
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("comments").where("visibility", "==", "PUBLIC").get()
  );
});

test("User reactions & joined_rooms subcollections enforce strict owner isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("reactions").doc("post_1").set({
      postId: "post_1",
      vote: "LIKE",
      echoed: true,
      bookmarked: false,
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("reactions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("reactions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("reactions").doc("post_1").get());
});

test("Rooms create, list, and counter updates work securely", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_1").set({
      slug: "unpopular",
      name: "Unpopular Opinions",
      description: "Speak freely without fear.",
      category: "Debate",
      anonymousRoom: true,
      accentKey: "VIOLET",
      guidelines: "Debate ideas, not people.",
      creatorUid: ALICE_UID,
      memberCount: 1,
      postCount: 0,
      visibility: "PUBLIC",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("rooms").where("visibility", "==", "PUBLIC").get());
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_1").update({
      memberCount: 2,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc("room_1").update({
      anonymousRoom: false,
      updatedAt: pastDate(1000),
    })
  );
});

test("Conversations and DirectMessages strictly isolate participants", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate(5000);
  const convId = `${ALICE_UID}_${BOB_UID}`;

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantHandles: { [ALICE_UID]: "alice", [BOB_UID]: "bob" },
      lastMessagePreview: "Voice note (0:06)",
      lastMessageKind: "VOICE",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").set({
      conversationId: convId,
      senderUid: BOB_UID,
      senderName: "Bob",
      participantUids: [ALICE_UID, BOB_UID],
      messageKind: "VOICE",
      audioBase64: "AAAAIGZ0eXBpc29t",
      durationMs: 6000,
      waveformPeaks: [0.3, 0.8, 0.5],
      transcript: "Hey Alice, loved your last voice post!",
      captionSegments: [{ startMs: 0, endMs: 6000, text: "Hey Alice, loved your last voice post!" }],
      createdAt: pastDate(1000),
    })
  );

  // Participants can query their conversations and messages
  await assertSucceeds(
    aliceDb.collection("conversations").where("participantUids", "array-contains", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb
      .collection("conversations")
      .doc(convId)
      .collection("messages")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );

  // Charlie (non-participant) is completely blocked from reading Alice & Bob's conversation/messages
  await assertFails(charlieDb.collection("conversations").doc(convId).get());
  await assertFails(charlieDb.collection("conversations").doc(convId).collection("messages").get());
  await assertFails(
    charlieDb
      .collection("conversations")
      .doc(convId)
      .collection("messages")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").get()
  );
});

test("Voice Masking presets, Voice Clapback quotes, and Dual-Side Hot Take Debates are validated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(8000);

  // Create Hot Take Debate + Clapback post with STUDIO_RADIO & GLITCH voice masking
  await assertSucceeds(
    aliceDb.collection("posts").doc("clap_hottake_1").set({
      authorUid: ALICE_UID,
      authorName: "Alice Studio",
      authorHandle: "alice_voice",
      authorAccentKey: "VIOLET",
      postKind: "FEED_VOICE",
      roomId: "room_anon_1",
      roomSlug: "unpopular",
      anonymousMode: true,
      anonymousAlias: "PrismFox_412",
      voiceDisguise: "STUDIO_RADIO",
      audioMediaId: "blob_reply_1",
      durationMs: 14000,
      waveformPeaks: [0.3, 0.8, 0.6, 0.9],
      transcript: "Actually I completely disagree with that quote.",
      captionSegments: [{ startMs: 0, endMs: 5000, text: "Actually I completely disagree with that quote." }],
      headlineText: "AI music will never replace live concerts",
      hashtags: ["music", "debate"],
      likeCount: 0,
      dislikeCount: 0,
      commentCount: 0,
      echoCount: 0,
      hotTakeMode: true,
      agreeCount: 0,
      disagreeCount: 0,
      quotedPostId: "post_orig_1",
      quotedAuthorName: "CipherOwl_74",
      quotedAudioMediaId: "blob_orig_1",
      quotedStartMs: 2000,
      quotedEndMs: 11000,
      quotedDisguise: "GLITCH",
      quotedTranscript: "Live shows are overrated.",
      quotedWaveformPeaks: [0.4, 0.7, 0.5],
      visibility: "PUBLIC",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject invalid voiceDisguise preset
  await assertFails(
    aliceDb.collection("posts").doc("bad_mask_post").set({
      authorUid: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice",
      postKind: "FEED_VOICE",
      anonymousMode: true,
      voiceDisguise: "ROBOT_HACK",
      durationMs: 5000,
      waveformPeaks: [0.5],
      transcript: "Invalid disguise",
      captionSegments: [],
      likeCount: 0,
      dislikeCount: 0,
      commentCount: 0,
      echoCount: 0,
      visibility: "PUBLIC",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob posts a DISAGREE audio reply using NEUTRAL_SHIFT mask and increments disagreeCount
  await assertSucceeds(
    bobDb.collection("posts").doc("clap_hottake_1").collection("comments").doc("debate_c1").set({
      postId: "clap_hottake_1",
      authorUid: BOB_UID,
      authorName: "Bob",
      authorHandle: "bob_v",
      anonymousMode: true,
      anonymousAlias: "EchoWarden_92",
      voiceDisguise: "NEUTRAL_SHIFT",
      stance: "DISAGREE",
      audioMediaId: "blob_c1",
      durationMs: 7000,
      waveformPeaks: [0.2, 0.8],
      transcript: "Concerts are way too expensive now.",
      captionSegments: [{ startMs: 0, endMs: 7000, text: "Concerts are way too expensive now." }],
      likeCount: 0,
      dislikeCount: 0,
      visibility: "PUBLIC",
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  await assertSucceeds(
    bobDb.collection("posts").doc("clap_hottake_1").update({
      commentCount: 1,
      disagreeCount: 1,
      updatedAt: pastDate(1000),
    })
  );

  // Reject invalid debate stance
  await assertFails(
    bobDb.collection("posts").doc("clap_hottake_1").collection("comments").doc("debate_bad").set({
      postId: "clap_hottake_1",
      authorUid: BOB_UID,
      authorName: "Bob",
      authorHandle: "bob_v",
      anonymousMode: false,
      voiceDisguise: "NONE",
      stance: "MAYBE",
      textBody: "Invalid stance",
      durationMs: 0,
      waveformPeaks: [],
      likeCount: 0,
      dislikeCount: 0,
      visibility: "PUBLIC",
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
});

test("Safety settings, mute/block relations, illegal content reports, and cursor pagination are enforced", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  // Alice saves private safety settings
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("safety_settings").doc("config").set({
      showUnfilteredControversial: false,
      collapseHeavilyDownvoted: true,
      downvoteThreshold: 5,
      updatedAt: ts,
    })
  );

  // Bob cannot read or overwrite Alice's safety settings
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("safety_settings").doc("config").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("safety_settings").doc("config").set({
      showUnfilteredControversial: true,
      collapseHeavilyDownvoted: false,
      downvoteThreshold: 5,
      updatedAt: ts,
    })
  );

  // Alice blocks Charlie and mutes Bob
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("safety_relations").doc(CHARLIE_UID).set({
      targetUid: CHARLIE_UID,
      targetName: "Charlie",
      relationType: "BLOCK",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("safety_relations").doc(BOB_UID).set({
      targetUid: BOB_UID,
      targetName: "Bob",
      relationType: "MUTE",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  // Bob cannot inspect Alice's blocked/muted list
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("safety_relations").get()
  );
  // Invalid relation type rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("safety_relations").doc("u_bad").set({
      targetUid: "u_bad",
      relationType: "BAN_FOREVER",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Structured illegal content report succeeds
  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_illegal_1").set({
      reporterUid: ALICE_UID,
      targetType: "POST",
      targetId: "post_bad_1",
      category: "VIOLENCE_THREATS",
      reason: "Credible Threats & Violence",
      details: "Explicit threat of physical violence in voice clip.",
      targetAuthorUid: CHARLIE_UID,
      urgentLegalReview: true,
      createdAt: ts,
    })
  );
  // Invalid report category rejected
  await assertFails(
    aliceDb.collection("reports").doc("rep_invalid_cat").set({
      reporterUid: ALICE_UID,
      targetType: "POST",
      targetId: "post_bad_1",
      category: "SPICY_OPINION",
      reason: "Invalid",
      createdAt: ts,
    })
  );
  // Bob cannot read Alice's report
  await assertFails(bobDb.collection("reports").doc("rep_illegal_1").get());

  // Word-level timestamped caption segments + Paging 3 cursor query
  for (let i = 1; i <= 3; i++) {
    await assertSucceeds(
      aliceDb.collection("posts").doc(`post_page_${i}`).set({
        authorUid: ALICE_UID,
        authorName: "Alice",
        authorHandle: "alice",
        postKind: "FEED_VOICE",
        anonymousMode: false,
        voiceDisguise: "NONE",
        durationMs: 6000,
        waveformPeaks: [0.3, 0.7],
        transcript: `Voice page item ${i}`,
        captionSegments: [
          {
            startMs: 0,
            endMs: 2500,
            text: `Voice page item ${i}`,
            words: [
              { startMs: 0, endMs: 600, word: "Voice" },
              { startMs: 650, endMs: 1200, word: "page" },
              { startMs: 1250, endMs: 1900, word: "item" },
              { startMs: 1950, endMs: 2500, word: `${i}` },
            ],
          },
        ],
        likeCount: 0,
        dislikeCount: 0,
        commentCount: 0,
        echoCount: 0,
        visibility: "PUBLIC",
        createdAt: ts,
        updatedAt: ts,
      })
    );
  }
  const page1 = await assertSucceeds(
    bobDb.collection("posts").where("visibility", "==", "PUBLIC").limit(2).get()
  );
  const lastDoc = page1.docs[page1.docs.length - 1];
  await assertSucceeds(
    bobDb.collection("posts").where("visibility", "==", "PUBLIC").startAfter(lastDoc).limit(2).get()
  );
});


