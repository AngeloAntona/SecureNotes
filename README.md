# MOBILE SYSTEM SECURITY: FIRST ASSIGNMENT
## ASSIGNMENT
(*due no later than **on 2024-10-24**, this exercise is **optional***) 
Write a program - secure notepad - the access to which will be secured by a password. After providing the password, the user should be able to display the stored note, change it, change the password. Providing an incorrect password should of course result in lack of access to the note and any possibility of changing the password. Avoid putting comments which identify you in the source code of your application (the code can be published, and the professor prefers to publish personal data only after explicit agreement; if you want them published, feel free to sign your name in the comments). 

Please send in your source code in a zip file to the professor, in a message with a **subject:  
BSM - prostykod - {studentnumber} {lastname} {firstname}**  
(Do not send in < nor > characters, and of course put in your owe personal information. Your student number has only numbers in it - no letters.)  
The body of the message is irrelevant. You can e.g. put in your comments regarding this lab in iambic pentameter, but you can also leave it blank.

## HOW TO USE THE APP
### Login with Password Screen (*MainActivity*)
- **Default Password**: When the user starts the app for the first time, the default password is `0000`.
### Notes Screen (*NotesActivity*)
- **Viewing and Editing a Note**: After logging in, the user can see a list of all saved notes. Each list item represents the title of a note.  By clicking on an existing note, the user can open and edit the content of the note.
- **Change Password**: From the notes screen, the user can change the password by clicking on the **M** button. This button opens a screen (***ModLockActiviy***)that allows the user to update their password.
- **Creating a New Note**: The user can add a new note using THE **+** button, which opens a blank screen where the user can input the title and content of the note.
- **Deleting a Note**: The user can delete an existing note by long-pressing it in the list. A confirmation popup will appear to confirm the deletion.
  
![Logo](ReadmeFiles/ClassScheme.png)
