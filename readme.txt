Reflect about your solution!

 Summary:


Alle Kommandos sollten funktionieren.
Die "Ausgaben" werden alle mit einem Prefix versehen (zB: alice.at >) um sich bei den Ausgaben besser orientieren zu k�nnen.
Die syntaktische �berpr�fung der Parameter/Parameteranzahl (zb: f�r !register oder !msg) habe ich aus Zeitgr�nden �bersprungen.
Die Shell habe ich nicht verwendet.
Der impliziete !lookup im Befehl habe ich leider nur durch einen Workaround l�sen k�nnen. Grund: Da die Funktionen im Client nicht blockierend sind kann man in den Methoden nicht das Ergebnis einer anderen verwenden.
Aus Zeitgr�nden konnte ich ebenfalls ein Problem beim !exit des Chatservers (UDPServer blockiert weitherhin den Port) nicht l�sen.
Es wurden ausschlie�licht "nested Classes" verwendet.
Den Executor Service habe ich nicht verwendet.
Die TCP Threads im chatserver werden evtl. nicht besonders gut verwaltet.