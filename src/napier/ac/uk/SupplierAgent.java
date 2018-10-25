package napier.ac.uk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jade.content.Concept;
import jade.content.ContentElement;
import jade.content.lang.Codec.CodecException;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import napier.ac.uk_ontology.elements.Computer;
import napier.ac.uk_ontology.elements.Order;
import napier.ac.uk_ontology.elements.computerComponents.ComputerComponent;
import napier.ac.uk_ontology.elements.predicates.CanManufacture;
import napier.ac.uk_ontology.elements.predicates.OwnsComponent;

public abstract class SupplierAgent extends Agent {
  private HashMap<ComputerComponent, Integer> componentsForSale; // component, price
  private AID tickerAgent;
  private ArrayList<AID> buyers = new ArrayList<>();
  
  private HashMap<AID, ComputerComponent> componentsApproved = new HashMap<>(); // List of the components order we said yes to
  private HashMap<AID, ComputerComponent> componentsConfirmed = new HashMap<>(); // List of the components and the agents that they are for

  @Override
  protected void setup() {}
    
  protected void register() {
    // add this agent to the yellow pages
    DFAgentDescription dfd = new DFAgentDescription();
    dfd.setName(getAID());
    ServiceDescription sd = new ServiceDescription();
    sd.setType("supplier");
    sd.setName(getLocalName() + "-supplier-agent");
    dfd.addServices(sd);
    try{
      DFService.register(this, dfd);
    }
    catch(FIPAException e){
      e.printStackTrace();
    }
  }
  
  public class TickerWaiter extends CyclicBehaviour {

    //behaviour to wait for a new day
    public TickerWaiter(Agent a) {
      super(a);
    }

    @Override
    public void action() {
      MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchContent("new day"),
          MessageTemplate.MatchContent("terminate"));
      ACLMessage msg = myAgent.receive(mt); 
      if(msg != null) {
        if(tickerAgent == null) {
          tickerAgent = msg.getSender();
        }
        if(msg.getContent().equals("new day")) {
          myAgent.addBehaviour(new FindBuyers(myAgent));
          
          ArrayList<Behaviour> cyclicBehaviours = new ArrayList<>();
          CyclicBehaviour os = new OffersServer(myAgent);
          myAgent.addBehaviour(os);
          cyclicBehaviours.add(os);
          
          myAgent.addBehaviour(new EndDayListener(myAgent, cyclicBehaviours));
        }
        else {
          //termination message to end simulation
          myAgent.doDelete();
        }
      }
      else{
        block();
      }
    }
    
    
    public class FindBuyers extends OneShotBehaviour {

      public FindBuyers(Agent a) {
        super(a);
      }

      @Override
      public void action() {
        DFAgentDescription buyerTemplate = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("buyer");
        buyerTemplate.addServices(sd);
        try{
          buyers.clear();
          DFAgentDescription[] agentsType  = DFService.search(myAgent,buyerTemplate); 
          for(int i=0; i<agentsType.length; i++){
            buyers.add(agentsType[i].getName()); // this is the AID
          }
        }
        catch(FIPAException e) {
          e.printStackTrace();
        }

      }

    }

  }
  
  public class OffersServer extends CyclicBehaviour {
    
    public OffersServer(Agent a) {
      super(a);
    }

    @Override
    public void action() {
      MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.QUERY_IF);
      ACLMessage msg = myAgent.receive(mt);
      if(msg != null) {
        try {
          ContentElement ce = null;
          
          // Print out the message content in SL
          System.out.println("Component message asked is: " + msg.getContent()); 

          // Let JADE convert from String to Java objects
          // Output will be a ContentElement
          ce = getContentManager().extractContent(msg);
          if (ce instanceof OwnsComponent) {
            OwnsComponent ownsComponent = (OwnsComponent) ce;
            ComputerComponent component = (ComputerComponent) ownsComponent.getComponent();
            
            // Extract the component print it to demonstrate use of the ontology
            System.out.println("The computer ordered is " + component.toString());
            
            
            // Accept all questions, we have unlimited stock
            ACLMessage reply = msg.createReply();
            componentsApproved.put(msg.getSender(), component); // Add to list of components that we said yes to, but not yet confirmed
            reply.setPerformative(ACLMessage.CONFIRM);
            
            System.out.println("\nSending response to the manufacturer. We own the component.");
            myAgent.send(reply);
          }
        }
        catch (CodecException ce) {
          ce.printStackTrace();
        }
        catch (OntologyException oe) {
          oe.printStackTrace();
        }
      } else {
        block();
      }
    }
  }
  
  
  private class SellBehaviour extends CyclicBehaviour{
    @Override
    public void action() {
      //This behaviour should only respond to REQUEST messages
      MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST); 
      ACLMessage msg = receive(mt);
      if(msg != null){
        try {
          ContentElement ce = null;
          System.out.println(msg.getContent()); //print out the message content in SL

          // Let JADE convert from String to Java objects
          // Output will be a ContentElement
          ce = getContentManager().extractContent(msg);
          if(ce instanceof Action) {
            Concept action = ((Action)ce).getAction();
            if (action instanceof Sell) {
              Sell order = (Sell)action;
              Item it = order.getItem();
              // Extract the CD name and print it to demonstrate use of the ontology
              if(it instanceof CD){
                CD cd = (CD)it;
                //check if seller has it in stock
                if(itemsForSale.containsKey(cd.getSerialNumber())) {
                  System.out.println("Selling CD " + cd.getName());
                }
                else {
                  System.out.println("You tried to order something out of stock!!!! Check first!");
                }

              }
            }

          }
        }

        catch (CodecException ce) {
          ce.printStackTrace();
        }
        catch (OntologyException oe) {
          oe.printStackTrace();
        }

      }
      else{
        block();
      }
    }

  }

  
  
  
  
  public class EndDayListener extends CyclicBehaviour {
    private int buyersFinished = 0;
    private List<Behaviour> toRemove;
    
    public EndDayListener(Agent a, List<Behaviour> toRemove) {
      super(a);
      this.toRemove = toRemove;
    }

    @Override
    public void action() {
      MessageTemplate mt = MessageTemplate.MatchContent("done");
      ACLMessage msg = myAgent.receive(mt);
      if(msg != null) {
        buyersFinished++;
      }
      else {
        block();
      }
      if(buyersFinished == buyers.size()) {
        //we are finished
        ACLMessage tick = new ACLMessage(ACLMessage.INFORM);
        tick.setContent("done");
        tick.addReceiver(tickerAgent);
        myAgent.send(tick);
        //remove behaviours
        for(Behaviour b : toRemove) {
          myAgent.removeBehaviour(b);
        }
        myAgent.removeBehaviour(this);
      }
    }
    
  }
}
