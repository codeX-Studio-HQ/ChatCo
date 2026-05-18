/**
 * Firebase Cloud Function: yeni mesajda alıcıya FCM data mesajı gönderir.
 * Kurulum: proje kökünde `npm install` (functions klasöründe), ardından `firebase deploy --only functions`
 */
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

exports.onChatMessageCreated = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const msg = snap.data();
    const chatId = event.params.chatId;
    const senderUid = msg.senderUid;
    if (!senderUid) return;

    const db = getFirestore();
    const chatSnap = await db.collection("chats").doc(chatId).get();
    const members = chatSnap.get("memberIds") || [];
    const recipientUid = members.find((u) => u !== senderUid);
    if (!recipientUid) return;

    const userSnap = await db.collection("users").doc(recipientUid).get();
    const token = userSnap.get("fcmToken");
    if (!token) return;

    const names = chatSnap.get("displayNames") || {};
    const title = names[senderUid] || "ChatCo";
    let body = msg.text || "";
    if (msg.type === "image") body = "Fotoğraf";
    if (!body) body = "Yeni mesaj";

    await getMessaging().send({
      token,
      data: {
        chatId: String(chatId),
        otherUid: String(senderUid),
        otherName: String(title),
        body: String(body),
      },
      android: { priority: "high" },
    });
  }
);
