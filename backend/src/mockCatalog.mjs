/**
 * Hand-written DEMO contexts for two additional provisional senses.
 * No LLM output, expert validation, or measured difficulty is claimed.
 */
export const CONTEXT_IDS=["campus","gaming","office","travel","sports","technology","finance","event","exam","application"];
const stories={
  "no-cap-truth": [
    [
      "Campus Notices",
      "@campusnews",
      "#CampusChaos",
      "No cap, the deadline got moved to Friday. It's on the portal now.",
      [
        "the portal banner popped up for me too",
        "did you take a screenshot?"
      ]
    ],
    [
      "Late Queue",
      "@lategamer",
      "#GamingMoments",
      "No cap, I landed that last shot with one HP left.",
      [
        "we watched the replay five times",
        "the clip is wild"
      ]
    ],
    [
      "Office Notes",
      "@worknotes",
      "#WorkLife",
      "No cap, the boss actually complimented our prototype today.",
      [
        "the whole team heard it",
        "someone frame that email"
      ]
    ],
    [
      "Trail Journal",
      "@hikechat",
      "#TravelTales",
      "No cap, we saw the sunrise from above the clouds on this hike.",
      [
        "you have the photo receipts",
        "it looks unreal up there"
      ]
    ],
    [
      "Final Whistle",
      "@matchlive",
      "#SportsTalk",
      "No cap, that was the loudest stadium I've ever been in.",
      [
        "my ears are still ringing",
        "the recording doesn't capture it"
      ]
    ],
    [
      "Dev Diary",
      "@devnotes",
      "#TechTalk",
      "No cap, the bug was one missing comma after six hours of debugging.",
      [
        "please commit that fix immediately",
        "I remember your error screenshot"
      ]
    ],
    [
      "Budget Diary",
      "@budgetchat",
      "#Adulting",
      "No cap, I saved half my salary this month.",
      [
        "show us the spreadsheet sometime",
        "that's a serious milestone"
      ]
    ],
    [
      "Stage Crew",
      "@stagecrew",
      "#EventLife",
      "No cap, the crowd sang the whole chorus without the singer.",
      [
        "the balcony footage caught it",
        "I got chills watching"
      ]
    ],
    [
      "Exam Hall",
      "@examweek",
      "#ExamWeek",
      "No cap, the exact practice problem showed up on the final.",
      [
        "everyone left the room in disbelief",
        "I wish I'd revised that page"
      ]
    ],
    [
      "Career Steps",
      "@careersteps",
      "#CareerTalk",
      "No cap, they offered me the internship during the call.",
      [
        "that's amazing, check your inbox",
        "you were smiling the whole afternoon"
      ]
    ]
  ],
  "lowkey-subtle": [
    [
      "Campus Journal",
      "@campusjournal",
      "#CampusChaos",
      "I'm lowkey excited for presentation day, but I haven't told my team yet.",
      [
        "you've been practicing in the empty classroom",
        "your poker face is impressive"
      ]
    ],
    [
      "Game Lounge",
      "@gamecorner",
      "#GamingMoments",
      "I'm lowkey rooting for the rival squad because my cousin is on it.",
      [
        "you got awfully quiet during their match",
        "your family ties are showing"
      ]
    ],
    [
      "Office Hours",
      "@officeafter",
      "#WorkLife",
      "I lowkey miss our old office, but I won't say that in the work chat.",
      [
        "I saw you keeping the old desk photo",
        "new office isn't quite the same"
      ]
    ],
    [
      "Wander Notes",
      "@tripnotes",
      "#TravelTales",
      "Lowkey hoping this trip gets extended. Not posting that in the group chat.",
      [
        "you keep checking the return date",
        "another day would be lovely"
      ]
    ],
    [
      "Sideline Talk",
      "@sideline",
      "#SportsTalk",
      "I'm lowkey cheering for the underdog from the back row.",
      [
        "I noticed that tiny fist pump",
        "trying not to upset your friends?"
      ]
    ],
    [
      "Dev Notes",
      "@devjournal",
      "#TechTalk",
      "Lowkey impressed with the new build. Don't tell the lead dev I said that.",
      [
        "you've opened that demo three times",
        "someone's warming up to it"
      ]
    ],
    [
      "Wallet Talk",
      "@walletclub",
      "#Adulting",
      "Lowkey nervous about the bill, even though I acted relaxed at dinner.",
      [
        "you checked the menu prices twice",
        "we can split it evenly"
      ]
    ],
    [
      "Event Diary",
      "@eventjournal",
      "#EventLife",
      "I'm lowkey looking forward to the reunion; pretending it's no big deal.",
      [
        "you picked an outfit a week early",
        "that's a very casual countdown"
      ]
    ],
    [
      "Exam Notes",
      "@studyspace",
      "#ExamWeek",
      "Lowkey worried about the exam tomorrow, but I keep saying I'm chill.",
      [
        "you've sharpened every pencil twice",
        "it'll help to get some sleep"
      ]
    ],
    [
      "Application Notes",
      "@careersteps",
      "#CareerTalk",
      "I'm lowkey hoping they reply to my application. Not telling everyone yet.",
      [
        "you keep refreshing your email",
        "fingers crossed for good news"
      ]
    ]
  ]
};
const distractors={
  'no-cap-truth':['hiding an unpleasant secret','making an obvious joke','changing the topic'],
  'lowkey-subtle':['in a loud and public way','without any interest','as a strict official rule']
};
const correctSlots=[1,3,0,2,1,3,0,2,0,3];
const levels=['easy','easy','easy','easy','easy','medium','medium','medium','hard','hard'];
export function additionalMockPackage(sense){
 const rows=stories[sense.senseId];
 if(!rows||rows.length!==10)throw Error('no-curated-demo-for-sense');
 return rows.map(([displayName,handle,hashtag,content,comments],i)=>{
  const selected=correctSlots[i];
  const options=[...distractors[sense.senseId]];
  options.splice(selected,0,sense.targetMeaning);
  return {id:sense.senseId+'-'+(i+1),slang:sense.term,targetMeaning:sense.targetMeaning,
    difficulty:levels[i],contextId:CONTEXT_IDS[i],
    post:{id:CONTEXT_IDS[i],displayName,handle,hashtag,content,
      comments:comments.map((text,j)=>({handle:'@reply'+(j+1)+'_'+CONTEXT_IDS[i],text}))},
    questionComment:(sense.senseId==='no-cap-truth'?[
      "Wait, no cap? Did somebody lose a hat?",
      "I'm lost — what does no cap mean? Is this about headwear?",
      "No cap? Like, are we supposed to take our caps off?"
    ]:[
      "What does lowkey mean here? Is there an actual key?",
      "Wait, lowkey? Are you talking about a small key?",
      "I'm confused — does lowkey refer to a lock or something?"
    ])[i%3],
    question:"What does '"+sense.term+"' mean in this post?",
    options,correctIndex:selected,explanation:"This example uses '"+sense.term+"' to mean '"+sense.targetMeaning+"'."};
 });
}
