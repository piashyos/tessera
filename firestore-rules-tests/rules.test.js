// Firestore security-rules tests for Tessera shared notes.
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
const EDITOR = 'editor-uid';
const VIEWER = 'viewer-uid';
const MANAGER = 'manager-uid';
const STRANGER = 'stranger-uid';
const NOTE_ID = 'note-1';

let testEnv;

function member(role) {
  return { role, displayName: role, email: `${role}@x.io`, photoUrl: null, joinedAt: 1 };
}

function sharedNoteData(members) {
  return {
    id: NOTE_ID,
    ownerId: OWNER,
    title: 'hello',
    content: 'body',
    tags: [],
    isPinned: false,
    isArchived: false,
    createdAt: 1,
    updatedAt: 1,
    deletedAt: null,
    members,
    memberIds: Object.keys(members),
  };
}

// Seeds a shared note with owner+editor+viewer+manager, bypassing rules.
async function seedNote() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(
      doc(ctx.firestore(), 'sharedNotes', NOTE_ID),
      sharedNoteData({
        [OWNER]: member('OWNER'),
        [MANAGER]: member('MANAGER'),
        [EDITOR]: member('EDITOR'),
        [VIEWER]: member('VIEWER'),
      }),
    );
  });
}

const db = (uid) => testEnv.authenticatedContext(uid).firestore();
const noteRef = (uid) => doc(db(uid), 'sharedNotes', NOTE_ID);

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

describe('usersByEmail index', () => {
  test('member can read the index (for invite lookup)', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'usersByEmail', 'a@x.io'), { uid: 'a', email: 'a@x.io' });
    });
    await assertSucceeds(getDoc(doc(db('anyone'), 'usersByEmail', 'a@x.io')));
  });

  test('user may only write their own index entry', async () => {
    await assertSucceeds(
      setDoc(doc(db('a'), 'usersByEmail', 'a@x.io'), { uid: 'a', email: 'a@x.io' }),
    );
    await assertFails(
      setDoc(doc(db('a'), 'usersByEmail', 'b@x.io'), { uid: 'b', email: 'b@x.io' }),
    );
  });
});

describe('sharedNotes read', () => {
  test('member can read', async () => {
    await seedNote();
    await assertSucceeds(getDoc(noteRef(EDITOR)));
  });

  test('non-member cannot read', async () => {
    await seedNote();
    await assertFails(getDoc(noteRef(STRANGER)));
  });
});

describe('sharedNotes create', () => {
  test('owner can create a note with self as OWNER', async () => {
    await assertSucceeds(
      setDoc(noteRef(OWNER), sharedNoteData({ [OWNER]: member('OWNER') })),
    );
  });

  test('cannot create a note owned by someone else', async () => {
    await assertFails(
      setDoc(noteRef(STRANGER), sharedNoteData({ [OWNER]: member('OWNER') })),
    );
  });

  test('cannot create unless self role is OWNER', async () => {
    await assertFails(
      setDoc(noteRef(OWNER), {
        ...sharedNoteData({ [OWNER]: member('EDITOR') }),
      }),
    );
  });
});

describe('sharedNotes content updates', () => {
  test('editor can edit content', async () => {
    await seedNote();
    await assertSucceeds(
      updateDoc(noteRef(EDITOR), { content: 'edited', updatedAt: 2 }),
    );
  });

  test('viewer cannot edit content', async () => {
    await seedNote();
    await assertFails(updateDoc(noteRef(VIEWER), { content: 'nope', updatedAt: 2 }));
  });

  test('non-member cannot edit content', async () => {
    await seedNote();
    await assertFails(updateDoc(noteRef(STRANGER), { content: 'nope', updatedAt: 2 }));
  });

  test('editor cannot soft-delete (set deletedAt) for all', async () => {
    await seedNote();
    await assertFails(updateDoc(noteRef(EDITOR), { deletedAt: 99, updatedAt: 2 }));
  });

  test('manager can soft-delete (set deletedAt) for all', async () => {
    await seedNote();
    await assertSucceeds(updateDoc(noteRef(MANAGER), { deletedAt: 99, updatedAt: 2 }));
  });

  test('owner can soft-delete (set deletedAt) for all', async () => {
    await seedNote();
    await assertSucceeds(updateDoc(noteRef(OWNER), { deletedAt: 99, updatedAt: 2 }));
  });

  test('editor cannot clear deletedAt (un-delete) for all', async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), 'sharedNotes', NOTE_ID),
        {
          ...sharedNoteData({
            [OWNER]: member('OWNER'),
            [MANAGER]: member('MANAGER'),
            [EDITOR]: member('EDITOR'),
            [VIEWER]: member('VIEWER'),
          }),
          deletedAt: 50,
        },
      );
    });
    await assertFails(updateDoc(noteRef(EDITOR), { deletedAt: null, updatedAt: 2 }));
  });

  test('editor ordinary content edit (no deletedAt change) still succeeds', async () => {
    await seedNote();
    await assertSucceeds(
      updateDoc(noteRef(EDITOR), { title: 'renamed', content: 'edited', updatedAt: 2 }),
    );
  });
});

describe('sharedNotes membership updates', () => {
  test('owner can add a member', async () => {
    await seedNote();
    await assertSucceeds(
      updateDoc(noteRef(OWNER), {
        'members.new-uid': member('EDITOR'),
        memberIds: arrayUnion('new-uid'),
      }),
    );
  });

  test('manager can change a role', async () => {
    await seedNote();
    await assertSucceeds(
      updateDoc(noteRef(MANAGER), { 'members.editor-uid.role': 'VIEWER' }),
    );
  });

  test('editor cannot change membership', async () => {
    await seedNote();
    await assertFails(
      updateDoc(noteRef(EDITOR), {
        'members.new-uid': member('EDITOR'),
        memberIds: arrayUnion('new-uid'),
      }),
    );
  });
});

describe('sharedNotes leave self', () => {
  test('viewer can remove themselves', async () => {
    await seedNote();
    await assertSucceeds(
      updateDoc(noteRef(VIEWER), {
        [`members.${VIEWER}`]: deleteField(),
        memberIds: arrayRemove(VIEWER),
      }),
    );
  });

  test('editor cannot remove another member', async () => {
    await seedNote();
    await assertFails(
      updateDoc(noteRef(EDITOR), {
        [`members.${VIEWER}`]: deleteField(),
        memberIds: arrayRemove(VIEWER),
      }),
    );
  });
});

describe('sharedNotes delete', () => {
  test('manager can delete the note for all', async () => {
    await seedNote();
    await assertSucceeds(deleteDoc(noteRef(MANAGER)));
  });

  test('viewer cannot delete the note', async () => {
    await seedNote();
    await assertFails(deleteDoc(noteRef(VIEWER)));
  });
});
