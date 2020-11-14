package uk.ac.bris.cs.scotlandyard.model;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {

	@Nonnull @Override public GameState build(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives) {

		return new MyGameState(setup, ImmutableSet.of(MrX.MRX),	ImmutableList.of(), mrX, detectives);
		//return new instance of MyGameState
	}

	private class MyGameState implements GameState {
		private GameSetup setup;
		private ImmutableSet<Piece> remaining;
		private ImmutableList<LogEntry> log;
		private Player mrX;
		private List<Player> detectives;
		private ImmutableList<Player> everyone;
		private ImmutableSet<Move> moves;
		private ImmutableSet<Piece> winner;
		private int roundnum = 0;


		//private as only builder in outer class can use it
		private MyGameState(final GameSetup setup,
							final ImmutableSet<Piece> remaining,
							final ImmutableList<LogEntry> log,
							final Player mrX,
							final List<Player> detectives) {

			this.setup = setup;
			this.remaining = remaining;
			this.log = log;
			this.mrX = mrX;
			this.detectives = detectives;
			this.everyone = ImmutableList.<Player>builder().add(this.mrX).addAll(this.detectives).build();
			this.moves = getAvailableMoves();

			//Checks
			if(setup == null || remaining == null || log == null || mrX == null || detectives == null){
				throw new NullPointerException();
			}
			if (setup.rounds.isEmpty()) throw new IllegalArgumentException();

			if (setup.graph.nodes().isEmpty() || setup.graph.edges().isEmpty()) throw new IllegalArgumentException();

			for (final var p : detectives) {
				if (p.has(Ticket.DOUBLE) || p.has(Ticket.SECRET)) {
					throw new IllegalArgumentException();
				}
			}

			for (final var p : detectives) {
				for (final var k : detectives) {
					if ((p != k) && (p.location() == k.location())) {
						throw new IllegalArgumentException();
					}
				}
			}

		}

		private ImmutableSet<Move.SingleMove> makeSingleMoves(
				GameSetup setup,
				List<Player> detectives,
				Player player,
				int source) {

			final var singleMoves = new HashSet<Move.SingleMove>();

			for (int destination : setup.graph.adjacentNodes(source)) {
				var occupied = false;
				for (Player d : detectives) {
					if (d.location() == destination)
						occupied = true; // make sure location is unoccupied
				}
				if (occupied) continue;


				for (Transport t : setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of())) {
					if (player.has(t.requiredTicket())) {
						singleMoves.add(new Move.SingleMove(player.piece(), source, t.requiredTicket(), destination));
					}

					// TODO: add moves to the destination via a Secret ticket if there are any left with the player
					if (player.has(Ticket.SECRET)) {
						singleMoves.add(new Move.SingleMove(player.piece(), source, Ticket.SECRET, destination));
					}
				}
			}
			return ImmutableSet.copyOf(singleMoves);
		}


		private ImmutableSet<Move.DoubleMove> makeDoubleMoves(
				GameSetup setup,
				List<Player> detectives,
				Player player,
				int source) {

			final var DoubleMoves = new HashSet<Move.DoubleMove>();

			if (player.has(Ticket.DOUBLE) && (setup.rounds.size() >= 2)) {

				for (int firstDestination : setup.graph.adjacentNodes(source)) {
					var occupied = false;
					for (Player d : detectives) {
						if (d.location() == firstDestination)
							occupied = true;
					}
					if (occupied) continue;

					for (Transport t : setup.graph.edgeValueOrDefault(source, firstDestination, ImmutableSet.of())) {
						if (player.has(t.requiredTicket()) || player.has(Ticket.SECRET)) {
							for (int secondDestination : setup.graph.adjacentNodes(firstDestination)) {
								var occupiedSecond = false;
								for (Player dd : detectives) {
									if (dd.location() == secondDestination)
										occupiedSecond = true;
								}
								if (occupiedSecond) continue;

								for (Transport tSecond : setup.graph.edgeValueOrDefault(firstDestination, secondDestination, ImmutableSet.of())) {
									if ((t.requiredTicket() == tSecond.requiredTicket() && player.hasAtLeast(t.requiredTicket(), 2)) || (player.has(tSecond.requiredTicket()) && t.requiredTicket() != tSecond.requiredTicket())) {
										DoubleMoves.add(new Move.DoubleMove(player.piece(), source, t.requiredTicket(), firstDestination, tSecond.requiredTicket(), secondDestination));
									}

									if (player.has(Ticket.SECRET) && player.has(tSecond.requiredTicket())) {
										DoubleMoves.add(new Move.DoubleMove(player.piece(), source, Ticket.SECRET, firstDestination, tSecond.requiredTicket(), secondDestination));
									}

									if (player.has(t.requiredTicket()) && player.has(Ticket.SECRET)) {
										DoubleMoves.add(new Move.DoubleMove(player.piece(), source, t.requiredTicket(), firstDestination, Ticket.SECRET, secondDestination));
									}

									if (player.hasAtLeast(Ticket.SECRET, 2)) {
										DoubleMoves.add(new Move.DoubleMove(player.piece(), source, Ticket.SECRET, firstDestination, Ticket.SECRET, secondDestination));
									}
								}
							}
						}
					}
				}
			}
			return ImmutableSet.copyOf(DoubleMoves);
		}

		@Nonnull
		@Override
		public GameSetup getSetup() {
			return setup;
		}


		@Nonnull
		@Override
		public ImmutableSet<Piece> getPlayers() {
			return ImmutableSet.<Piece>copyOf(
					this.everyone.stream().map(Player::piece).collect(Collectors.toSet())
			);
		}

		@Nonnull
		@Override
		public Optional<Integer> getDetectiveLocation(Detective detective) {
			for (final var p : detectives) {
				if (p.piece() == detective) return Optional.of(p.location());
			}
			return Optional.empty();
		}

		private class PlayerTicketBoard implements TicketBoard {
			ImmutableMap<Ticket, Integer> tickets;

			public PlayerTicketBoard(ImmutableMap<Ticket, Integer> tickets) {
				this.tickets = tickets;}

			@Override
			public int getCount(@Nonnull Ticket ticket) {
				if(!(tickets.containsKey(ticket))) throw new IllegalArgumentException();
				return tickets.get(ticket);
			}

			public boolean isEmpty(){
				ImmutableCollection<Integer> num =tickets.values();
				for (Integer i: num){
					if(i != 0) return false;
				}
				return true;
			}
		}

		@Nonnull
		@Override
		public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			for (final var p : everyone) {
				if (p.piece()==(piece)) {
					PlayerTicketBoard playerTB = new PlayerTicketBoard(p.tickets());
					return Optional.of(playerTB);
				}
			}
			return Optional.empty();
		}

		@Nonnull
		@Override
		public ImmutableList<LogEntry> getMrXTravelLog() {
			return log;
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getWinner() {
			return ImmutableSet.<Piece>of();
		}

		@Nonnull
		@Override
		public ImmutableSet<Move> getAvailableMoves() {
			final Set<Move> allMoves = new HashSet<Move>();
			for (final var p : everyone){
				if(remaining.contains(p.piece())){
					allMoves.addAll(makeSingleMoves(setup, detectives, p, p.location()));
					if(p.isMrX() && p.has(Ticket.DOUBLE) && setup.rounds.size()>2){
						allMoves.addAll(makeDoubleMoves(setup, detectives, p, p.location()));
					}
				}
			}
			return ImmutableSet.copyOf(allMoves);
		}


//		helper method to update the log
		public List<LogEntry> updateLog (Move move, List<Integer> ListOfDestination) {
			List<LogEntry> newLog = new ArrayList<LogEntry>();
			List<Ticket> ticketsUsed = new ArrayList<Ticket>();

			for (final var t: move.tickets()){
				ticketsUsed.add(t);
			}
			Ticket ticket1 = ticketsUsed.get(0);
			Ticket ticket2 = (ticketsUsed.size()>1) ? ticketsUsed.get(1) : null;
			newLog.addAll(log);

			if(ListOfDestination.size()==1){ //move is singlemove
				roundnum+=1;
				if (setup.rounds.get(roundnum-1)) {
					newLog.add(LogEntry.reveal(ticket1, ListOfDestination.get(0))); }
				else{
					newLog.add(LogEntry.hidden(ticket1));
				}
			}
			else{ // move is doublemove
				roundnum+=2;
				if (setup.rounds.get(roundnum-2)){
					newLog.add(LogEntry.reveal(ticket1, ListOfDestination.get(0)));
				}
				else{
					newLog.add(LogEntry.hidden((ticket1)));
				}
				if (setup.rounds.get(roundnum-1)){
					newLog.add(LogEntry.reveal(ticket2, ListOfDestination.get(1)));
				}
				else{
					newLog.add(LogEntry.hidden((ticket2)));
				}
			}
		return newLog;
		}


		@Nonnull
		@Override
		public GameState advance(Move move) {
			if (!moves.contains(move)) throw new IllegalArgumentException("Illegal move: " + move);

			Set<Piece> newRemaining = new HashSet<Piece>();
			List<LogEntry> newLog = new ArrayList<LogEntry>();
			Piece playerMoved = move.commencedBy();
			List<Player> newDetectives = new ArrayList<Player>();


			for (final var p: detectives) {
				if (p.piece() != playerMoved) {
					newDetectives.add(p);
				}
			}

			int finalDestination = move.visit(new Move.Visitor<Integer>(){
				@Override
				public Integer visit(Move.SingleMove singleMove) {
					return singleMove.destination;
				}
				@Override
				public Integer visit(Move.DoubleMove doubleMove) {
					return doubleMove.destination2;
				}
			});

			//to check if singlemove or doublemove
			List<Integer> ListOfDestination = move.visit(new Move.Visitor<List<Integer>>(){
				@Override
				public List<Integer> visit(Move.SingleMove singleMove) {
					List<Integer> list = new ArrayList<Integer>();
					list.add(singleMove.destination);
					return list;
				}
				@Override
				public List<Integer> visit(Move.DoubleMove doubleMove) {
					List<Integer> list = new ArrayList<Integer>();
					list.add(doubleMove.destination1);
					list.add(doubleMove.destination2);
					return list;
				}
			});


			if (playerMoved.isMrX()) {
				mrX = mrX.use(move.tickets());
				mrX = mrX.at(finalDestination);
				newLog = updateLog(move, ListOfDestination);

			}
			else{
				for(final var p : detectives){
					if (p.piece() == playerMoved) {
						Player pTickets = p.use(move.tickets());
						Player pDesti = pTickets.at(finalDestination);
						newDetectives.add(pDesti);
						mrX = mrX.give(move.tickets());
					}
				}
			}

			if(playerMoved.isMrX()){ //beginning of each round, only mrX is in remaining set
				for(Player p : detectives){
					newRemaining.add(p.piece());
				}
			}
			else{
				for(Piece p : remaining){
					if (p != playerMoved) newRemaining.add(p);
				}
			}

			return new MyGameState(setup, ImmutableSet.copyOf(newRemaining), ImmutableList.copyOf(newLog), mrX, ImmutableList.copyOf(newDetectives));

		}

	}
}