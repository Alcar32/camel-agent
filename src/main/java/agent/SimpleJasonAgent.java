///////////////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2013 Cranefield S., Ranathunga S. All rights reserved.               /
// ---------------------------------------------------------------------------------- /
// This file is part of camel_jason.                                                  /

//    camel_jason is free software: you can redistribute it and/or modify             /
//   it under the terms of the GNU Lesser General Public License as published by      /
//    the Free Software Foundation, either version 3 of the License, or               /
//    (at your option) any later version.                                             /

//    camel_jason is distributed in the hope that it will be useful,                  /
//    but WITHOUT ANY WARRANTY; without even the implied warranty of                  /
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   /
//    GNU Lesser General Public License for more details.                             /

//    You should have received a copy of the GNU Lesser General Public License        /
//    along with camel_jason.  If not, see <http://www.gnu.org/licenses/>.            /  
///////////////////////////////////////////////////////////////////////////////////////

package agent;

import jason.architecture.AgArch;
import jason.asSemantics.ActionExec;
import jason.asSemantics.Agent;
import jason.asSemantics.Circumstance;
import jason.asSemantics.Message;
import jason.asSemantics.TransitionSystem;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.runtime.Settings;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author surangika
 * This class extends the logic in the SimpleJasonAgent class of the Jason distribution
 */
public class SimpleJasonAgent extends AgArch implements Serializable{
	static final long serialVersionUID =2;
    private static Logger logger = Logger.getLogger(SimpleJasonAgent.class.getName());
    private AgentContainer container;
    Agent ag;
    private String name;
    Queue<Message> localMsgQueue;
    Queue<Literal> pers_percepts;
    Queue<Literal> temp_percepts;
    private boolean hasStarted;
    private Vector<AgentConsumer> myConsumers;  

    public SimpleJasonAgent(AgentContainer container, String path, String fName) {
    	this.container = container;
    	localMsgQueue = new ConcurrentLinkedQueue<Message>();
    	pers_percepts = new ConcurrentLinkedQueue<Literal>();
    	temp_percepts = new ConcurrentLinkedQueue<Literal>();
    	myConsumers = new Vector<AgentConsumer>();
    	
        // set up the Jason agent
        try {
            ag = new Agent();
            name = fName;   
            hasStarted = false;
            new TransitionSystem(ag, new Circumstance(), new Settings(), this);  
            //Initialise agent from the .asl file
            ag.initAg(path);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Init error", e);
        }
    }
    
    public void addToMyConsumers(AgentConsumer myConsumer)
    {
    	synchronized (myConsumers) {    		
    		myConsumers.add( myConsumer);				
		}    	
    }   
    
    /**
     * Agent execution cycle, runs in its own thread
     */
    public void run() {
    	hasStarted = true;
    	 Thread thread = new Thread(){
    		    public void run(){
    		    	try {
    		    		
    		            while (isRunning()) {
    		                // calls the Jason engine to perform one reasoning cycle
    		                logger.fine("Reasoning....");
    		                getTS().reasoningCycle();
    		            }
    		        } catch (Exception e) {
    		            logger.log(Level.SEVERE, "Run error", e);
    		            hasStarted = false;
    		        }
    		    }
    		  };    		 
    		  thread.start();
    }

    public String getAgName() {
        return name;
    }
    
    public void updateMsgQueue(Message m)
    {
    	localMsgQueue.offer(m);    		
    }
    
    public boolean getHasStarted()
    {
    	return hasStarted;
    }
    
    /**
     * @param percept
     * @param annotations
     * @param updateMode
     * @param persistent
     * Updates the transient and persistent percepts using messages received from the camel exchange
     */
    public void updatePerceptList(String percept, String annotations, String updateMode, String persistent)
    {    	
    	Literal l = Literal.parseLiteral(percept);    	
    	
		List<String> annots = Arrays.asList(annotations.split(","));
    	
    	//Add the separately recieved annotations to the literal
    	if (l != null && annots != null)
		 {
    		 for(String as : annots)
			 {
				 try {
					if (as != "")
						l.addAnnot(ASSyntax.parseTerm(as));
				} catch (Exception e) {
					// TODO: handle exception
				}
			 }			
		 }
    	
    	//Transient percepts are simply added to the transient queue
    	if(persistent.equals("false"))
    	{
    		synchronized(temp_percepts)
    		{
    			temp_percepts.offer(l);
    		}
    	}
    	    	
    	if(persistent.equals("true")){
    		//If updateMode is + (default option) and an identical percept is not already in the persistent queue, add the percept to the persistent queue 
    		if(updateMode.equals("+"))
    		{
    			synchronized (pers_percepts) {
					boolean lit_exists = false;
					Iterator<Literal> i = pers_percepts.iterator();
					while (i.hasNext()) {
						Literal lit = i.next();
						if (lit.compareTo(l) != -1) {
							lit_exists = true;
							break;
						}
					}
					if (!lit_exists){
						pers_percepts.offer(l);
					}
				}
    		}
    		//If the update mode is -+, remove all the percepts having the same functor and arity form the persistent queue, and add the received percept
    		else if(updateMode.equals("-+"))
        	{
        		synchronized (pers_percepts) {        			
    				Iterator<Literal> i = pers_percepts.iterator();
    				while (i.hasNext()) {
    				   Literal lit = i.next();
    				   if (lit.getFunctor().equals(l.getFunctor()) && lit.getArity() == l.getArity())
    					   i.remove();
    				}
    				pers_percepts.offer(l);
        		}
        	}    		
    	}
    }

    // Agent calls this method to receive the transient and persistent percepts 
    @Override
    public List<Literal> perceive() {
        List<Literal> l = new ArrayList<Literal>();       
        
        //Remove transient percepts from the transient queue, and add them to agent's internal percept list 
        while (!temp_percepts.isEmpty())
        {        	
        	Literal li = temp_percepts.poll();
            l.add(li);
        }
        //Add persistent percepts to agent's internal percept list, but do not remove them from the persistent queue 
        Iterator<Literal> i = pers_percepts.iterator();
        while (i.hasNext()){
        	Literal li = i.next();
        	l.add(li);
        }        
        return l;
    }

    // this method get the agent actions
    @Override
    public void act(ActionExec action, List<ActionExec> feedback) {
        JasonAction a = new JasonAction(action, feedback, getAgName());
        
        //Find all consumers that can handle "action"s
        List<AgentConsumer> actionCons = getValidConsumers("action");
       
        //if there is no consumer to handle this action
        if (actionCons.size()==0)
        {
        	action.setResult(false);
        	feedback.add(action);
        }
        else
        {
        	boolean r = false;
        
        	for(AgentConsumer myConsumer: actionCons)
        	{
        		if(myConsumer.agentActed(a))//if at least one action succeeded
        		{        			
        			r = true;
        			action.setResult(true);
                	feedback.add(action);
                	break;
        		}        			
        	}
        	if (!r)
        	{
        		action.setResult(false);
            	feedback.add(action);
        	}
        }    		        
    }

    @Override
    public boolean canSleep() {
       // return true;
    	return localMsgQueue.isEmpty() && isRunning();
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    // a very simple implementation of sleep
    @Override
    public void sleep() {
        try {
        Thread.sleep(1000);
        } catch (InterruptedException e) {}
    }    
    
    @Override
    public void sendMsg(jason.asSemantics.Message m) throws Exception {   
    	//Send to Camel exchange if the receipient name starts with container
    	if (m.getReceiver().startsWith("container"))
    	{
    		for(AgentConsumer myConsumer: getValidConsumers("message"))
            	myConsumer.agentMessaged(m);
    	}
    	//Else send as a local Jason message
    	else 
    		container.getCamelMessages(m, m.getReceiver());    		
    }

    @Override
    public void broadcast(jason.asSemantics.Message m) throws Exception {
    	//Send to Camel exchange if the recipient name starts with container
    	if (name.startsWith("container")) 
    	{
    		m.setReceiver("all");
    		for(AgentConsumer myConsumer: getValidConsumers("message"))
    			myConsumer.agentMessaged(m); 
    	}   
    	else
    		container.getCamelMessages(m, "all");
    }

    @Override
    public void checkMail() {
    	while (!localMsgQueue.isEmpty()) {
            Message im = localMsgQueue.poll();
            getTS().getC().getMailBox().offer(im);            
        }
    }  
    
    /**
     * @param consumerType
     * @return
     * Returns the set of consumers for a given consumer type (messages or actions)
     */
    public List<AgentConsumer> getValidConsumers(String consumerType)
    {
    	List<AgentConsumer> consumers = new ArrayList<AgentConsumer>();
    	for(AgentConsumer myConsumer: myConsumers)
    	{    		
    		if (myConsumer.toString().contains(consumerType))
    			consumers.add(myConsumer);
    	}
    	return consumers;
    }
}
