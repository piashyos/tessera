// End-to-end full-lifecycle flow test for Tessera shared notes against the
// Firestore emulator. Unlike rules.test.js (which exercises each rule in
// isolation), this drives a single sequential two-user scenario at the
// Firestore-document level: owner shares -> invites -> invitee edits ->
// owner downgrades -> invitee leaves -> owner unshares.
//
// Run with the firestore emulator: `npm test` (wraps `firebase emulators:exec`).
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  arrayRemove,
  arrayUnion,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  setDoc,
  updateDoc,
} from 'firebase/firestore';

const OWNER = 'owner-uid';
const INVITEE = 'invitee-uid';
const NOTE_ID = 'note-1';

let testEnv;

const db = (uid) => testEnv.authenticatedContext(uid).firestore();

// Local helper (separate scope from rules.test.js); includes the email field
// because the invite flow stores the resolved member's email on the member doc.
function member(role, email) {
  return { role, displayName: role, email, photoUrl: null, joinedAt: 1 };
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'tessera-rules-test',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

describe('full share lifecycle (two users)', () => {
  test('owner shares, invites, invitee edits, owner downgrades, removes, unshares', async () => {
    // 0. Both users publish their own usersByEmail index entry (as the
    //    AuthRepository does on sign-in). Each may write only their own entry.
    await assertSucceeds(
      setDoc(doc(db(OWNER), 'usersByEmail', 'owner@x.io'), {
        uid: OWNER,
        email: 'owner@x.io',
      }),
    );
    await assertSucceeds(
      setDoc(doc(db(INVITEE), 'usersByEmail', 'invitee@x.io'), {
        uid: INVITEE,
        email: 'invitee@x.io',
      }),
    );

    // 1. Owner creates the shared note with self as OWNER (full document).
    const ownerNote = doc(db(OWNER), 'sharedNotes', NOTE_ID);
    await assertSucceeds(
      setDoc(ownerNote, {
        id: NOTE_ID,
        ownerId: OWNER,
        title: 'T',
        content: 'orig',
        tags: [],
        isPinned: false,
        isArchived: false,
        createdAt: 1,
        updatedAt: 1,
        deletedAt: null,
        members: { [OWNER]: member('OWNER', 'owner@x.io') },
        memberIds: [OWNER],
      }),
    );

    // 2. Owner resolves the invitee's email via the index (invite lookup),
    //    then adds them as EDITOR (members path + memberIds arrayUnion).
    await assertSucceeds(getDoc(doc(db(OWNER), 'usersByEmail', 'invitee@x.io')));
    await assertSucceeds(
      updateDoc(ownerNote, {
        [`members.${INVITEE}`]: member('EDITOR', 'invitee@x.io'),
        memberIds: arrayUnion(INVITEE),
      }),
    );

    // 3. Invitee can now read the shared doc and edit content only.
    await assertSucceeds(getDoc(doc(db(INVITEE), 'sharedNotes', NOTE_ID)));
    await assertSucceeds(
      updateDoc(doc(db(INVITEE), 'sharedNotes', NOTE_ID), {
        content: 'edited',
        updatedAt: 2,
      }),
    );

    // 4. Owner downgrades the invitee to VIEWER; a viewer can no longer edit.
    await assertSucceeds(
      updateDoc(ownerNote, { [`members.${INVITEE}.role`]: 'VIEWER' }),
    );
    await assertFails(
      updateDoc(doc(db(INVITEE), 'sharedNotes', NOTE_ID), {
        content: 'nope',
        updatedAt: 3,
      }),
    );

    // 5. The (now viewer) invitee may still remove themselves (leave).
    await assertSucceeds(
      updateDoc(doc(db(INVITEE), 'sharedNotes', NOTE_ID), {
        [`members.${INVITEE}`]: deleteField(),
        memberIds: arrayRemove(INVITEE),
      }),
    );

    // 6. After leaving, the former member can no longer read the shared doc.
    await assertFails(getDoc(doc(db(INVITEE), 'sharedNotes', NOTE_ID)));

    // 7. Owner unshares: write the note back to the private subtree, then
    //    delete the shared doc. The private rule only requires auth.uid == uid,
    //    so no extra field constraints apply to this payload.
    await assertSucceeds(
      setDoc(doc(db(OWNER), 'users', OWNER, 'notes', NOTE_ID), {
        id: NOTE_ID,
        title: 'T',
        content: 'edited',
        updatedAt: 4,
        deletedAt: null,
      }),
    );
    await assertSucceeds(deleteDoc(ownerNote));
  });
});
