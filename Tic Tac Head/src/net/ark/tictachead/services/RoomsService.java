package net.ark.tictachead.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import net.ark.tictachead.activities.GameActivity;
import net.ark.tictachead.helpers.RecordManager;
import net.ark.tictachead.helpers.Utilities;
import net.ark.tictachead.models.FriendManager;
import net.ark.tictachead.models.GameManager;
import net.ark.tictachead.models.Tictactoe;
import net.gogo.server.onii.api.tictachead.Tictachead;
import net.gogo.server.onii.api.tictachead.model.CollectionResponseRoom;
import net.gogo.server.onii.api.tictachead.model.Room;
import android.app.IntentService;
import android.content.Intent;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.json.gson.GsonFactory;

public class RoomsService extends IntentService {
	public RoomsService() {
		super(SERVICE_NAME);
	}

	@Override
	protected void onHandleIntent(Intent intent) {	
		//Skip if no intent
		if (intent == null) return;

		//Get connection
		Tictachead.Builder Builder 	= new Tictachead.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), null);
		Tictachead Connection		= Builder.build();

		ArrayList<Long> New       = new ArrayList<Long>();
		ArrayList<Long> Changes   = new ArrayList<Long>();
		
		try {
			//Get 
			CollectionResponseRoom Result = Connection.listRoom().execute();
			if (Result != null) {
				//Get players
				List<Room> Items = Result.getItems();
				if (Items != null) {
					//get current games
					Hashtable<Long, Tictactoe> Games = GameManager.instance().getAllGames();
					for (int i = 0; i < Items.size(); i++) {
						Boolean Finish = Items.get(i).getFinished();
						String ID1 = Items.get(i).getPlayers().get(0).toString();
						String ID2 = Items.get(i).getPlayers().get(1).toString();
						if ((ID1.equals(RecordManager.instance().getID()) || ID2.equals(RecordManager.instance().getID())) && (Finish == null || !Finish.booleanValue())) {
							//Get existing games
							Tictactoe NewGame   = new Tictactoe(Items.get(i));
							Tictactoe Game      = Games.get(NewGame.getOpponent());
							if (Game != null) {
								//If game is already sent, and mine say enemy turn + server said my turn
								long Opponent 	= NewGame.getOpponent();
								boolean IsSent 	= !GameManager.instance().isQueueing(Opponent);
								if (IsSent && !Game.isMyTurn() && NewGame.isMyTurn()) {
									//Save
									Changes.add(Long.valueOf(Opponent));
									GameManager.instance().getGame(Long.valueOf(Opponent)).save(NewGame);
								}
							} else {
								//Add new game
								GameManager.instance().putGame(NewGame);
								New.add(Long.valueOf(NewGame.getOpponent()));
								FriendManager.instance().addOpponent(NewGame.getOpponent());
								FriendManager.instance().setActiveOpponent(NewGame.getOpponent());
							}
						}
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//If there's update game
		if (!Changes.isEmpty() || !New.isEmpty()) {
			//Show and create head
			Intent HeadIntent = new Intent(this, HeadService.class);
			HeadIntent.putExtra(HeadService.EXTRA_CREATE, true);
			startService(HeadIntent);

			//Send broadcast telling there's change
			Intent Broadcast = new Intent(GameActivity.GAME_CHANGED);
			Broadcast.putExtra(EXTRA_CHALLENGES, Utilities.createArray(New));
			Broadcast.putExtra(EXTRA_OPPONENTS, Utilities.createArray(Changes));
			sendBroadcast(Broadcast);
		}
	}
	
	//Constants
	public static final String EXTRA_OPPONENTS	= "opponents";
	public static final String EXTRA_CHALLENGES = "challenges";
	protected static final String SERVICE_NAME 	= "net.ark.tictachead.RoomsService";
	public static final String ACTION       	= "net.ark.tictachead.Rooms";
}
