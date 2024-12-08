L’applicazione NoteApp è progettata per proteggere i dati sensibili (le note) e la Master Key utilizzata per cifrarle, tramite una combinazione di password dell’utente, derivazione di chiavi sicure, biometria (opzionale) e crittografia robusta. L’obiettivo è impedire l’accesso non autorizzato, anche in caso di compromissione del dispositivo, e garantire la segretezza e l’integrità delle note.

# Meccanismi di Autenticazione

## Password

* Impostazione della Password all’Avvio: Al primo utilizzo, l’utente è obbligato a creare una password senza valori di default. Questo previene accessi non autorizzati.
* Password Hashing con bcrypt: La password non viene mai salvata in chiaro. Viene generato un hash tramite l’algoritmo bcrypt con un costo (BCRYPT_COST) sufficiente a rendere il brute-force molto costoso. In questo modo, anche in caso di accesso fisico ai file dell’app, un attaccante non potrà facilmente risalire alla password.
* Controllo di Complessità: La password deve rispettare criteri di robustezza (min. 8 caratteri, almeno una lettera maiuscola, una minuscola, un numero e un carattere speciale). Ciò riduce il rischio di password banali e facilmente indovinabili.
* No Password di Default: Non avendo password preimpostate, si elimina una comune vulnerabilità iniziale.

# Biometria (Facoltativa)

* Keystore Hardware-Backed: L’app può memorizzare una versione cifrata della Master Key tramite il Keystore di Android, che sfrutta l’hardware TEE (Trusted Execution Environment). L’uso della chiave estratta dal Keystore richiede autenticazione biometrica (impronta digitale, riconoscimento del volto o altro metodo supportato).
* UserAuthenticationRequired: La chiave biometrica è generata con parametri che impongono l’autenticazione dell’utente per ogni utilizzo. Non basta avere il device compromesso; serve la biometria dell’utente per sbloccare la chiave biometrica. Questo è un fattore chiave per impedire l’uso non autorizzato della chiave, anche con privilegi elevati (root).

# Crittografia delle Note

## Master Key e Derivazione

* Master Key Generata e Cifrata con Password: All’iniziale impostazione della password, l’app genera una Master Key (32 byte). Questa Master Key non è mai salvata in chiaro su disco. Invece, viene cifrata usando una chiave derivata dalla password dell’utente.
* Derivazione della Chiave dalla Password con PBKDF2: Quando l’utente inserisce la password, l’app usa PBKDF2 (con SHA-256, 100.000 iterazioni e un salt univoco memorizzato in sicurezza) per ricavare una chiave simmetrica (Password-Derived Key). Questo processo rende estremamente costoso tentare di indovinare la password, proteggendo contro gli attacchi a forza bruta.
* Cifratura AES-GCM della Master Key: La Master Key, una volta generata, viene cifrata con la Password-Derived Key usando AES in modalità GCM. Questa modalità garantisce sia la confidenzialità che l’integrità dei dati. Per cambiare password, basta decrittare la Master Key con la vecchia password e ricifrarla con la nuova Password-Derived Key, senza ricifrare tutte le note.

## Cifratura delle Note

* AES-256 in Modalità GCM: Le note vengono cifrate con AES/GCM a 256 bit, usando la Master Key in chiaro (caricata in memoria solo dopo l’autenticazione). AES è uno standard di crittografia simmetrica ampiamente riconosciuto per la sua robustezza, e la modalità GCM fornisce autenticazione del messaggio (integrità), impedendo modifiche non rilevate ai dati cifrati.
* IV Unico per Ogni Cifratura: L’algoritmo AES/GCM richiede un vettore di inizializzazione (IV) unico per ogni operazione. L’app genera IV casuali per ogni nota o operazione, garantendo che la stessa chiave non sia mai usata due volte allo stesso modo, riducendo i rischi di attacchi noti.
* Salvataggio su Disco: Le note crittografate (IV + ciphertext) vengono salvate su disco. Senza la Master Key corretta, un attaccante non può decifrare le note. Neppure avendo accesso fisico al dispositivo o ai file potrà recuperare i dati in chiaro.

# Gestione dell’Accesso e Sicurezza Aggiuntiva

## Limitazione dei Tentativi e Blocco Temporaneo

* Lockout dopo Tentativi Falliti Multipli: Se l’utente sbaglia la password troppe volte (ad es. 5 tentativi), l’account si blocca per un certo intervallo (es. 1 minuto). Questo riduce la probabilità di successo di attacchi a forza bruta.

## Scadenza della Sessione

* Timeout della Sessione: L’app richiede la ri-autenticazione dopo un periodo di inattività (es. 5 minuti). Questo impedisce ad un malintenzionato di accedere alle note se il dispositivo è sbloccato ma lasciato incustodito per troppo tempo.
* Cancellazione della sessionKey in RAM: Quando la sessione scade o l’utente esce, la Master Key viene rimossa dalla memoria. Questo previene il rischio di recuperare la Master Key dalla RAM.

Sicurezza del Codice

* Librerie Affidabili: Vengono usate librerie consolidate (BCrypt per l’hashing della password, PBKDF2 per la derivazione della chiave, AES/GCM per la cifratura) fornite dalla piattaforma Android e da pacchetti affidabili.
* Nessun Log di Dati Sensibili: L’app evita di loggare password, chiavi o plaintext delle note.
* Error Handling Sicuro: I messaggi di errore non rivelano dettagli sull’algoritmo di cifratura o sulla password, mostrando solo informazioni minime e user-friendly.
