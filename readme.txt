Reflect about your solution!

 Summary:


Alle Kommandos sollten funktionieren.
Die "Ausgaben" werden alle mit einem Prefix versehen (zB: alice.at >) um sich bei den Ausgaben besser orientieren zu können.
Die syntaktische Überprüfung der Parameter/Parameteranzahl (zb: für !register oder !msg) habe ich aus Zeitgründen übersprungen.
Die Shell habe ich nicht verwendet.
Der impliziete !lookup im Befehl habe ich leider nur durch einen Workaround lösen können. Grund: Da die Funktionen im Client nicht blockierend sind kann man in den Methoden nicht das Ergebnis einer anderen verwenden.
Aus Zeitgründen konnte ich ebenfalls ein Problem beim !exit des Chatservers (UDPServer blockiert weitherhin den Port) nicht lösen.
Es wurden ausschließlicht "nested Classes" verwendet.
Den Executor Service habe ich nicht verwendet.
Die TCP Threads im chatserver werden evtl. nicht besonders gut verwaltet.