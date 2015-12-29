package org.jboss.aerogear.unifiedpush.dao;

import java.util.Date;
import java.util.List;

import org.jboss.aerogear.unifiedpush.api.DocumentMessage;
import org.jboss.aerogear.unifiedpush.api.PushApplication;

public interface DocumentDao {
	
	void create(DocumentMessage document);
	
	List<String> findPushDocumentsNewer(PushApplication pushApplication, Date date);
	
	List<String> findAliasDocumentsNewer(PushApplication pushApplication, String alias, String qualifier, Date date);
	
}
