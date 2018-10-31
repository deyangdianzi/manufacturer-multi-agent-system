package napier.ac.uk_ontology.actions;

import jade.content.AgentAction;
import jade.content.onto.annotations.Slot;
import jade.core.AID;
import napier.ac.uk_ontology.concepts.ComputerComponent;

public class BuyComponent implements AgentAction {
  private static final long serialVersionUID = 1L;
  
  private AID buyer;
  private ComputerComponent component;
  
  @Slot(mandatory = true)
  public AID getBuyer() {
    return buyer;
  }
  public void setBuyer(AID buyer) {
    this.buyer = buyer;
  }
  @Slot(mandatory = true)
  public ComputerComponent getComponent() {
    return component;
  }
  public void setComponent(ComputerComponent component) {
    this.component = component;
  }
  
  
}