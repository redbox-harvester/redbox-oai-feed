ReDBox OAI-PMH Feed  
===============
This project allows institutions to push data to the <a href='https://github.com/redbox-mint/oai-server'>OAI-PMH server</a>, by sending a JMS text message containing the expected JSON document format.

Installing
====
This project is an implementation of a <a href='https://github.com/redbox-harvester/json-harvester-client'>Harvester</a>, and has been configured to deploy inside a <a href='https://github.com/redbox-harvester/json-harvester-manager'>Harvester Manager</a>. 

To quickly install the entire stack, there is installer script tested with <a href='http://nectar.org.au/'>"NeCTAR CentOS 6.5 x86_64"</a>. Of course, this script comes with certain assumptions specific to the image, so you are certainly encouraged to modify the script to meet your needs. Run the ff. commands as root:

    wget https://raw.github.com/redbox-harvester/redbox-oai-feed/master/support/install/redbox_oaipmh_feed.sh
    ./redbox_oaipmh_feed.sh 
 
Grab a cuppa as the script downloads the internets and sets up the Harvester Manager and the OAI-PMH Feed. 
 
If everything went well, you can view the server's available metadata formats here:

    http://localhost/oai-server/?verb=ListMetadataFormats     

You can also optionally <a href='https://github.com/redbox-harvester/redbox-oai-feed/blob/master/support/install/addSampleEacRecord.groovy'>insert sample data</a>. You may run the ff. command on the same directory used on the previous commands:
    
    groovy addSampleEacRecord.groovy
 
When running the command, you may have to wait for a few minutes as it downloads its dependencies. A couple of minutes or so after it finishes, you can then view the sample record at:
    
    http://localhost/oai-server/?verb=ListRecords&metadataPrefix=eac-cpf 

That's it folks!
