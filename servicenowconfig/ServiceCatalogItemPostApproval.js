try {
    var client_id = gs.getProperty('ping.client_id');
    var client_secret = gs.getProperty('ping.client_secret');
    var scope = gs.getProperty('ping.oauth.scope');
    var tenant_url = gs.getProperty('ping.tenant_url');
    var requestTypeId = gs.getProperty("ping.entitlement.request_type");
    var workflowId = gs.getProperty("ping.entitlement.workflowid");
    var r = new sn_ws.RESTMessageV2('Ping AIC Token', 'Default POST');
    r.setStringParameterNoEscape('client_id', client_id);
    r.setStringParameterNoEscape('scope', scope);
    r.setStringParameterNoEscape('client_secret', client_secret);
    var response = r.execute();
    var responseBody = JSON.parse(response.getBody());
    var access_token = responseBody.access_token;
    // Get request item and catalog item details
    var details = "Requested Item Details:\n";
    var grReqItem = new GlideRecord('sc_req_item');
    grReqItem.get(current.sys_id);
    var ritmId = "";
    ritmId = grReqItem.number.toString();
    var grCatItem = new GlideRecord('sc_cat_item');
    grCatItem.get(grReqItem.cat_item);
    var itemID = grCatItem.sys_id;
    var govCatalogID = grCatItem.u_gov_cat_item_id.toString();
    details += "ID:" + current.sys_id +"\n";
    details += "Number: " + grReqItem.number + "\n";
    details += "Name:" + grCatItem.name + "\n";
    details += "Item ID:" + grCatItem.sys_id + "\n";
    details += "Short Description: " + current.short_description + "\n";
    details += "Opened By: " + current.opened_by.getDisplayValue() + "\n";
    details += "Requested For: "+ grReqItem.requested_for.user_name + "\n";
    details += "Gov Catalog ID: "+ govCatalogID + "\n";
    // Comments
    var arr = [];
    var apv = new GlideRecord('sysapproval_approver');
    apv.addQuery('sysapproval', current.sys_id);
    apv.query();
    while (apv.next()) { // Go through all approvals with comments
        var approverHistory = "Approval Comments: from " + apv.approver.name;
        var commentGr = new GlideRecord('sys_journal_field');
        commentGr.addQuery('element_id', current.sys_id);
        commentGr.addQuery('element', 'comments');
        commentGr.orderBy('sys_created_on');
        commentGr.query();
        while(commentGr.next()){
            approverHistory += commentGr.value.toString();
        }
        arr.push(approverHistory);
    }
    var approver_hist = arr.join('\n');
    details += approver_hist + "\n";
    gs.log("Request Details:\n"+ details,"Ping RITM Post Approval Info");
    // Invoke IGA Workflow
    var getUserRequest = new sn_ws.RESTMessageV2();
    getUserRequest.setLogLevel('All');
    getUserRequest.setEndpoint(tenant_url+"/openidm/managed/alpha_user");
    getUserRequest.setHttpMethod('GET');
    getUserRequest.setQueryParameter('_queryFilter', 'userName eq "'+grReqItem.requested_for.user_name+'"');
    getUserRequest.setQueryParameter('_pageSize', '10');
    getUserRequest.setQueryParameter('_totalPagedResultsPolicy', 'EXACT');
    getUserRequest.setQueryParameter('_fields', '_id,userName,givenName,sn,mail');
    getUserRequest.setRequestHeader('Authorization', 'Bearer ' + access_token);

    var userResponse = getUserRequest.execute();
    var userResponseBody = JSON.parse(userResponse.getBody());
    gs.log("IDM Response "+JSON.stringify(userResponseBody),"Ping RITM Post Approval Info");
    var userId = "";
    var userName = "";
    var userEmail = "";
    if (userResponseBody.result && userResponseBody.result.length > 0) {
        var user = userResponseBody.result[0];
        userId = user._id;
        userName = user.userName;
        userEmail = user.mail;
        var userDetails =" IDM User Details\n";
        userDetails += userId +"\n";
        userDetails += userName +"\n";
        userDetails += userEmail +"\n";
    }
    gs.log("Submitting request to Ping","Ping IGA Submission");

    var oPingRequest = new sn_ws.RESTMessageV2();
    oPingRequest.setLogLevel('All');
    var request_url = tenant_url+"/iga/governance/requests/"+requestTypeId;
    oPingRequest.setEndpoint(request_url);
    oPingRequest.setQueryParameter('_action','publish');
    oPingRequest.setHttpMethod("POST");
    oPingRequest.setRequestHeader('Content-Type','application/json');
    oPingRequest.setRequestHeader('Authorization', 'Bearer ' + access_token);
    var oPayload = {
        common: {
            priority: "low",
            externalRequestId: ritmId,
            isDraft: false,
            workflowId:workflowId,
            context: {
                type: "service-now"
            }
        },
        custom: {
            entitlementId: govCatalogID,
            userId: userId,
            serviceNowAudit: details
        }
    };
    oPingRequest.setRequestBody(JSON.stringify(oPayload));
    gs.log("IGA Payload\n"+JSON.stringify(oPayload),"Ping RITM Post Approval Info");
    var oPingResponse = oPingRequest.execute();
    var oPingResponseBody = JSON.parse(oPingResponse.getBody());
    gs.log("IGA Response "+JSON.stringify(oPingResponseBody),"Ping RITM Post Approval Info");
}
catch(ex) {
    var message = ex.message;
    gs.log("Error in script\n"+message,"Ping RITM Post Approval Info");
}