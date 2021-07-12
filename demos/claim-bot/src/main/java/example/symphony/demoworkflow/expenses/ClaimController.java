package example.symphony.demoworkflow.expenses;

import org.finos.symphony.toolkit.workflow.annotations.Exposed;
import org.finos.symphony.toolkit.workflow.content.Addressable;
import org.finos.symphony.toolkit.workflow.content.User;
import org.finos.symphony.toolkit.workflow.response.FormResponse;
import org.springframework.stereotype.Controller;

import example.symphony.demoworkflow.expenses.Claim.Status;

@Controller
public class ClaimController {


	@Exposed(value = "open", description="Begin New Expense Claim")
	public static Claim open(StartClaim c) {
		Claim out = new Claim();
		out.description = c.description;
		out.amount = c.amount;
		return out;
	}

	@Exposed(formClass = Claim.class, value="approve", description = "Approve Claim")
	public Claim approve(Claim c, User currentUser) {
		if (c.status == Status.OPEN) {
			c.approvedBy = currentUser;
			c.status = Status.APPROVED;
		}
		return c;
	}
	
	@Exposed(value="new", description = "New Full Expense Form") 
	public FormResponse full(Addressable room) {
		return new FormResponse(room, new Claim(), true);
	}
	
	
}
