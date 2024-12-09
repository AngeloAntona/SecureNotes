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

## Sicurezza del Codice

* Librerie Affidabili: Vengono usate librerie consolidate (BCrypt per l’hashing della password, PBKDF2 per la derivazione della chiave, AES/GCM per la cifratura) fornite dalla piattaforma Android e da pacchetti affidabili.
* Nessun Log di Dati Sensibili: L’app evita di loggare password, chiavi o plaintext delle note.
* Error Handling Sicuro: I messaggi di errore non rivelano dettagli sull’algoritmo di cifratura o sulla password, mostrando solo informazioni minime e user-friendly.

___

# Descrizione dei flussi di accesso con Password / Autenticazione biometrica

Di seguito un quadro chiaro e completo del rapporto tra autenticazione biometrica, autenticazione con password e la Master Key, evidenziando i flussi di sicurezza e i passaggi crittografici coinvolti:

## Concetti di Base
* Master Key: È la chiave AES principale, generata casualmente la prima volta che si imposta una password. Non viene mai salvata in chiaro su disco. Tutte le note sono cifrate con questa Master Key.
* Autenticazione con Password: L’utente inserisce una password complessa. Dalla password si deriva una chiave per decifrare la Master Key.
* Autenticazione Biometrica: Opzionale. L’utente può attivare l’autenticazione biometrica (impronta digitale o altro metodo supportato) per accedere senza digitare la password. La Master Key può essere cifrata anche con una chiave custodita nel Keystore hardware-backed di Android, sbloccabile con la biometria.

## Flusso di Sicurezza con Password
1.	Input dell’Utente: L’utente inserisce la password.
2.	Verifica Password:
* La password dell’utente, dopo essere stata inserita, non viene mai salvata in chiaro.
* L’app controlla l’hash della password salvato (ottenuto precedentemente con bcrypt). Se la password è corretta, si passa allo step successivo. Altrimenti, si incrementa il conteggio dei tentativi falliti.
3.	Derivazione della Chiave (PBKDF2):
* Viene usato PBKDF2 con il salt memorizzato e un elevato numero di iterazioni per derivare una chiave simmetrica (Password-Derived Key) a partire dalla password.
* Questo processo trasforma la password in una chiave robusta, resistente a brute force.
4.	Decrittazione della Master Key:
* La Master Key è salvata nel dispositivo soltanto in forma cifrata con la Password-Derived Key.
* Ora che abbiamo la Password-Derived Key, possiamo usarla per decifrare la Master Key cifrata. Viene usato AES/GCM: il risultato è la Master Key in chiaro, presente soltanto in RAM.
5.	Accesso alle Note:
* Con la Master Key in chiaro, l’app può decifrare ogni nota.
* La Master Key resta in memoria finché dura la sessione. Se l’utente chiude l’app o la sessione scade, la Master Key è rimossa dalla RAM.

### Riassunto del flusso con password:
Utente -> Inserisce password -> (Check hash) -> OK -> PBKDF2 -> Ottiene Password-Derived Key -> Decripta Master Key -> Master Key in RAM -> Decripta note.

## Flusso di Sicurezza con Biometria

La biometria è un metodo alternativo che l’utente può attivare dopo aver impostato una password. Funziona così:
	1.	Attivazione Biometria (una tantum):
	•	L’utente inserisce la password (flusso password visto sopra).
	•	Con la Master Key in chiaro, l’app usa il Keystore hardware-backed di Android per cifrare la Master Key con una chiave AES/GCM generata all’interno del Keystore. Questa chiave richiede autenticazione biometrica per essere usata.
	•	Ora abbiamo due versioni cifrate della Master Key: una con la Password-Derived Key e una con la chiave biometrica dal Keystore.
	2.	Accesso con Impronta (o altra Biometria):
	•	L’utente avvia l’app e sceglie l’autenticazione biometrica.
	•	L’app mostra il prompt biometrico. Al successo, Android fornisce un Cipher pronto per decrittare la Master Key cifrata con la chiave del Keystore.
	•	Non serve inserire la password, perché la Master Key si ottiene direttamente decifrando la versione cifrata con la chiave biometrica hardware-backed. Questo avviene completamente all’interno dell’enclave sicura del dispositivo (Trusted Execution Environment).
	3.	Decrittazione della Master Key tramite Biometria:
	•	Il Cipher sbloccato dalla biometria decripta la Master Key cifrata con chiave biometrica.
	•	La Master Key in chiaro è ora in RAM, esattamente come avviene con la password.
	4.	Accesso alle Note:
	•	Con la Master Key in chiaro, l’app può decifrare ogni nota, senza che l’utente abbia digitato la password.

### Riassunto del flusso biometrico:
Utente -> Autenticazione biometrica (impronta) -> Keystore sblocca chiave AES/GCM -> Decripta Master Key cifrata biometrica -> Master Key in RAM -> Decripta note.

## Confronto e Riepilogo

* Con Password: L’utente fornisce una password. Con PBKDF2 si ottiene una chiave per decrittare la Master Key.
* Con Biometria: L’utente appoggia il dito sul sensore. Il device, tramite il Keystore e il TEE, fornisce direttamente un Cipher per decrittare la Master Key, senza inserire la password.

In entrambi i casi, il risultato finale è lo stesso:
La Master Key viene caricata in RAM, e con essa l’app può decifrare o cifrare le note. Tuttavia, con la biometria non si digita la password, perché la Master Key è già cifrata in modo da poter essere decrittata dal Keystore con l’autenticazione biometrica.

## Sicurezza del Keystore e del Flusso Biometrico

* La chiave biometrica del Keystore non è mai accessibile in chiaro dallo spazio utente. Senza la biometria dell’utente, la Master Key non può essere sbloccata.
* Anche con privilegi root, un attaccante non può estrarre la chiave hardware dal TEE né ingannare il prompt biometrico.
