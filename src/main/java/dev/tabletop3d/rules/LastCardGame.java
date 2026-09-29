package dev.tabletop3d.rules;

import java.util.*;

/** Seeded 52-card shedding game. Public views reveal counts and played cards only. */
public final class LastCardGame implements HandGame {
    private static final List<String> COLORS=List.of("r","y","b","p");
    private static final List<Piece> CARDS=cards();
    private final List<List<Integer>> hands=new ArrayList<>();
    private final List<Integer> deck=new ArrayList<>(),pile=new ArrayList<>(),placements=new ArrayList<>();
    private final SplittableRandom random;
    private final boolean allPlaces;
    private int current,direction=1,pendingDraw,drawRank=-1;
    private boolean declared;
    private String activeColor="",result="ongoing",lastAction="Ready to play";

    public LastCardGame(int players,long seed){this(players,seed,false);}
    public LastCardGame(int players,long seed,boolean allPlaces){
        if(players<2||players>4)throw new IllegalArgumentException("Last Card requires 2 to 4 players");
        this.allPlaces=allPlaces;random=new SplittableRandom(seed);
        for(int card=0;card<CARDS.size();card++)deck.add(card);shuffle();
        for(int seat=0;seat<players;seat++)hands.add(new ArrayList<>());
        for(int card=0;card<5;card++)for(int seat=0;seat<players;seat++)drawCards(seat,1);
        current=random.nextInt(players);
    }
    @Override public String id(){return "lastcard";}
    @Override public int playerCount(){return hands.size();}
    @Override public int currentPlayer(){return current;}
    @Override public boolean finished(){return !result.equals("ongoing");}
    @Override public String outcome(){return result;}
    public List<Integer> placements(){return List.copyOf(placements);}
    @Override public List<Piece> hand(int seat){return hands.get(seat).stream().map(CARDS::get).toList();}
    @Override public List<Piece> discards(int seat){return List.of();}
    @Override public List<Piece> exposed(int seat){return List.of();}
    @Override public int handSize(int seat){return hands.get(seat).size();}
    @Override public int deckSize(){return deck.size();}
    @Override public List<Cell> cells(){
        return List.of(new Cell("deck",0,0,"Deck "+deck.size(),-1),new Cell("discard",2,0,pile.isEmpty()?"":CARDS.get(pile.getLast()).face(),-1));
    }
    @Override public List<String> legalActions(int seat){
        if(finished()||seat!=current)return List.of();List<String> actions=new ArrayList<>();
        for(int card:hands.get(seat))if(playable(card)){
            if(card>=48)for(String color:COLORS)actions.add("play:"+card+":"+color);else actions.add("play:"+card);
        }
        if(!declared&&handSize(seat)==2&&!actions.isEmpty())actions.addFirst("declare");
        if(pendingDraw>0||actions.isEmpty())actions.add("draw");
        return List.copyOf(actions);
    }
    @Override public List<String> actionsForCell(int seat,String id){
        return "deck".equals(id)&&legalActions(seat).contains("draw")?List.of("draw"):List.of();
    }
    private boolean playable(int card){
        if(pendingDraw>0)return rank(card)==drawRank;
        return pile.isEmpty()||card>=48||COLORS.get(card/12).equals(activeColor)||rank(card)==rank(pile.getLast());
    }
    @Override public void apply(int seat,String action){
        if(action==null||!legalActions(seat).contains(action))throw new IllegalArgumentException("Invalid Last Card action");
        if(action.equals("declare")){declared=true;lastAction="Player "+(seat+1)+" declared Last Card";return;}
        if(action.equals("draw")){
            int count=drawCards(seat,pendingDraw>0?pendingDraw:1);pendingDraw=0;drawRank=-1;declared=false;
            lastAction="Player "+(seat+1)+" drew "+count+" cards";advance(1);return;
        }
        String[] parts=action.split(":");int card=Integer.parseInt(parts[1]),rank=rank(card);
        boolean penalty=handSize(seat)==2&&!declared;declared=false;
        hands.get(seat).remove(Integer.valueOf(card));pile.add(card);activeColor=card>=48?parts[2]:COLORS.get(card/12);
        lastAction="Player "+(seat+1)+" played "+CARDS.get(card).face()+(card>=48?" ("+activeColor+")":"");
        if(penalty)lastAction+="; missing Last Card declaration, penalty "+drawCards(seat,5)+" cards";
        if(rank==9)direction=-direction;
        if(rank==10||rank==11){pendingDraw+=rank==10?2:3;drawRank=rank;}
        if(handSize(seat)==0){
            placements.add(seat);
            if(!allPlaces||placements.size()==playerCount()-1){
                if(allPlaces)for(int other=0;other<playerCount();other++)if(!placements.contains(other))placements.add(other);
                result="winner:"+placements.getFirst();return;
            }
        }
        advance(rank==8?2:1);
    }
    private void advance(int count){
        for(int step=0;step<count;step++)do{current=Math.floorMod(current+direction,playerCount());}while(placements.contains(current));
    }
    private int drawCards(int seat,int count){
        int drawn=0;
        while(drawn<count){
            if(deck.isEmpty()){
                if(pile.size()<2)break;
                int top=pile.removeLast();deck.addAll(pile);pile.clear();pile.add(top);shuffle();
            }
            hands.get(seat).add(deck.removeLast());drawn++;
        }
        return drawn;
    }
    private void shuffle(){for(int i=deck.size()-1;i>0;i--)Collections.swap(deck,i,random.nextInt(i+1));}
    private static int rank(int card){return card>=48?12:card%12;}
    private static List<Piece> cards(){
        List<Piece> cards=new ArrayList<>();List<String> ranks=List.of("1","2","3","4","5","6","7","8","Skip","Reverse","Draw2","Draw3");
        for(String color:COLORS)for(String rank:ranks)cards.add(new Piece(String.valueOf(cards.size()),color+rank));
        for(int i=0;i<4;i++)cards.add(new Piece(String.valueOf(cards.size()),"wild"));return List.copyOf(cards);
    }
    @Override public Map<String,String> publicInfo(){
        return Map.of("rules","Match color or symbol; wild chooses color; only matching draw cards stack; declare at two cards or draw five; drawing ends the turn",
            "rulesVariant","last-card-52-"+(allPlaces?"all-places":"first-place")+"-v1","phase",finished()?"Finished":"Play a card",
            "turn","Player "+(current+1),"color",activeColor,"direction",direction==1?"Clockwise":"Counterclockwise",
            "drawPenalty",String.valueOf(pendingDraw),"handSizes",hands.stream().map(List::size).toList().toString(),"placements",placements.toString(),"lastAction",lastAction);
    }
}
