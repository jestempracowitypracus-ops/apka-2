// Powiadomienia push dla aplikacji Pomoc Drogowa.
// - kierowca dostaje powiadomienie, gdy przydzielono mu zlecenie,
// - dyspozytorzy dostają powiadomienie o wiadomościach i zmianach statusu od kierowcy,
// - kierowca dostaje powiadomienie o wiadomościach od dyspozytora.

const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
setGlobalOptions({ region: "europe-central2", maxInstances: 5 });

const db = getFirestore();

async function tokensOf(userDocs) {
  const result = [];
  for (const doc of userDocs) {
    const data = doc.data() || {};
    if (data.active === false) continue;
    for (const token of data.fcmTokens || []) result.push({ uid: doc.id, token });
  }
  return result;
}

async function driverTokens(driverId) {
  if (!driverId) return [];
  const doc = await db.collection("users").doc(driverId).get();
  return doc.exists ? tokensOf([doc]) : [];
}

async function dispatcherTokens() {
  const snap = await db.collection("users").where("role", "==", "dispatcher").get();
  return tokensOf(snap.docs);
}

async function send(targets, { title, body, orderId, channel }) {
  if (targets.length === 0) return;
  const response = await getMessaging().sendEachForMulticast({
    tokens: targets.map((t) => t.token),
    notification: { title, body },
    data: { orderId, title, body },
    android: {
      priority: "high",
      notification: { channelId: channel, sound: "default" },
    },
  });

  // Usuwamy tokeny urządzeń, które już nie istnieją (np. odinstalowana aplikacja).
  const cleanup = [];
  response.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
      const { uid, token } = targets[i];
      cleanup.push(db.collection("users").doc(uid).update({ fcmTokens: FieldValue.arrayRemove(token) }));
    }
  });
  await Promise.all(cleanup);
}

function orderLabel(order) {
  const parts = [order.vehicle, order.plate].filter(Boolean).join(" ");
  return `#${order.number || ""} ${parts}`.trim();
}

// Nowe zlecenie lub zmiana kierowcy -> powiadom kierowcę.
exports.onOrderAssigned = onDocumentWritten("orders/{orderId}", async (event) => {
  const before = event.data.before.exists ? event.data.before.data() : null;
  const after = event.data.after.exists ? event.data.after.data() : null;
  if (!after || !after.driverId) return;
  if (before && before.driverId === after.driverId) return;

  await send(await driverTokens(after.driverId), {
    title: `Nowe zlecenie ${orderLabel(after)}`,
    body: after.pickupAddress || after.description || "Otwórz, aby zobaczyć szczegóły",
    orderId: event.params.orderId,
    channel: "orders",
  });
});

// Nowa wiadomość (komentarz, zdjęcie, głosówka, dane, zmiana statusu).
exports.onMessageCreated = onDocumentCreated("orders/{orderId}/messages/{messageId}", async (event) => {
  const msg = event.data.data();
  const orderId = event.params.orderId;
  const orderDoc = await db.collection("orders").doc(orderId).get();
  if (!orderDoc.exists) return;
  const order = orderDoc.data();

  const previews = {
    TEXT: msg.text,
    PHOTO: "📷 Zdjęcie",
    AUDIO: "🎤 Wiadomość głosowa",
    DATA: "📋 Dane dodatkowe",
    PROTOCOL: "📄 Protokół odbioru pojazdu",
    SYSTEM: msg.text,
  };
  const body = `${msg.senderName}: ${previews[msg.type] || "nowa wiadomość"}`;

  let targets;
  if (msg.senderRole === "driver") {
    targets = await dispatcherTokens();
  } else {
    targets = await driverTokens(order.driverId);
  }
  // Nie wysyłamy powiadomienia do autora wiadomości.
  targets = targets.filter((t) => t.uid !== msg.senderId);

  await send(targets, {
    title: `Zlecenie ${orderLabel(order)}`,
    body,
    orderId,
    channel: "messages",
  });
});
