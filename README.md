# Pomoc Drogowa – zlecenia dla kierowców

Aplikacja na Androida do przesyłania zleceń kierowcom pomocy drogowej.

## Co potrafi

**Dyspozytor**
- tworzy zlecenia (klient, telefon, pojazd, nr rej., skąd, dokąd, opis) i przydziela je kierowcy,
- widzi wszystkie zlecenia (w toku i zakończone) oraz ich status na żywo,
- zakłada konta kierowców i innych dyspozytorów, może wyłączyć konto,
- pisze komentarze, wysyła zdjęcia i głosówki w zleceniu, może zmienić status albo usunąć zlecenie.

**Kierowca**
- ma swój profil (imię, telefon, pojazd) i widzi tylko swoje zlecenia,
- dostaje powiadomienie o nowym zleceniu,
- jednym przyciskiem zmienia status: Przyjęte → W drodze → Na miejscu → Holowanie → Zakończone,
- dzwoni do klienta i włącza nawigację do miejsca zdarzenia,
- wysyła zdjęcia (z aparatu albo z galerii), komentarze, **głosówki** i **dane dodatkowe**
  (VIN, przebieg, paliwo, uszkodzenia, nr szkody, pobrana kwota, forma płatności).

Wszystko, co kierowca wyśle, dyspozytor widzi od razu w zleceniu i dostaje powiadomienie.

## Technologia

- Android (Kotlin, Jetpack Compose), Android 8.0 lub nowszy.
- Firebase: logowanie (Authentication), baza (Firestore), pliki (Storage), powiadomienia (Cloud Messaging + Cloud Functions).

## Uruchomienie – krok po kroku (jednorazowo)

### 1. Projekt Firebase
1. Wejdź na https://console.firebase.google.com i utwórz projekt (np. „pomoc-drogowa”).
2. Przełącz projekt na plan **Blaze** (płatność za użycie). Plan jest potrzebny do przechowywania zdjęć i głosówek
   oraz do powiadomień. Mała firma zwykle mieści się w darmowych limitach. Warto ustawić alert budżetu, np. na 20 zł.
3. **Authentication** → Rozpocznij → włącz metodę **E-mail/hasło**.
4. **Firestore Database** → Utwórz bazę → lokalizacja **europe-central2 (Warszawa)** → tryb produkcyjny.
5. **Storage** → Rozpocznij (ta sama lokalizacja).
6. Ustawienia projektu (⚙️) → **Dodaj aplikację → Android** → nazwa pakietu: `pl.pomocdrogowa.zlecenia`
   → pobierz plik **google-services.json**.

### 2. Reguły bezpieczeństwa i powiadomienia
Na komputerze z zainstalowanym Node.js:
```bash
npm install -g firebase-tools
firebase login
cd firebase
firebase use --add            # wybierz swój projekt
cd functions && npm install && cd ..
firebase deploy               # wgrywa reguły Firestore/Storage i funkcje powiadomień
```

### 3. Zbudowanie aplikacji
1. W repozytorium na GitHubie: **Settings → Secrets and variables → Actions → New repository secret**.
   - Nazwa: `GOOGLE_SERVICES_JSON`
   - Wartość: cała zawartość pliku `google-services.json`.
2. Wejdź w zakładkę **Actions → Budowanie aplikacji (APK) → Run workflow** (albo wypchnij zmianę).
3. Gotowy plik APK:
   - z gałęzi `main` trafia do zakładki **Releases** – otwórz ją na telefonie i pobierz `PomocDrogowa-….apk`,
   - z każdej innej gałęzi – w podsumowaniu przebiegu w Actions (artefakt `PomocDrogowa-APK`).
4. Na telefonie zezwól na instalację z nieznanych źródeł i zainstaluj APK.
   Kolejne wersje instalują się „na” poprzednią – dane nie znikają.

### 4. Pierwsze logowanie
1. Pierwsza osoba, która otworzy aplikację, zobaczy ekran **„Pierwsze uruchomienie – załóż konto dyspozytora”**.
   To konto głównego dyspozytora. Później ta opcja znika.
2. Dyspozytor: ikona 👥 (Kierowcy) → **Dodaj konto** → podaj imię, e-mail, hasło startowe, telefon i pojazd.
3. Przekaż kierowcy e-mail i hasło – loguje się nimi na swoim telefonie.
   Hasło może potem zmienić w „Mój profil → Zmień hasło”.

## Budowanie lokalnie (Android Studio)
Otwórz folder w Android Studio, wrzuć `google-services.json` do katalogu `app/` i uruchom.
Plik `google-services.json` nie jest trzymany w repozytorium (jest w `.gitignore`).

## Struktura
```
app/src/main/java/pl/pomocdrogowa/zlecenia/
  data/        modele i cała komunikacja z Firebase (Repository.kt)
  ui/auth      logowanie i pierwsze uruchomienie
  ui/orders    lista zleceń, formularz, szczegóły z czatem (zdjęcia, głosówki, dane)
  ui/drivers   konta kierowców i profil
  util/        nagrywanie/odtwarzanie dźwięku, kompresja zdjęć
firebase/      reguły bezpieczeństwa i funkcje powiadomień
```

> Klucz podpisu `app/signing.keystore` jest w repozytorium celowo – dzięki temu każda wersja z GitHub Actions
> instaluje się jako aktualizacja. Jeśli kiedyś aplikacja trafi do Google Play, trzeba utworzyć nowy, prywatny klucz.
