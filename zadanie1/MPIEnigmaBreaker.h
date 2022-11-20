#include "EnigmaBreaker.h"
#include "MessageComparator.h"
#include <array>
#include <cvector.h>
#include <stdint.h>

struct ProcessInitialValues {
  uint start = 0;
  uint end = 0;
};

class MPIEnigmaBreaker : public EnigmaBreaker {
private:
  bool solutionFound(uint *rotorSettingsProposal);

public:
  MPIEnigmaBreaker(Enigma *enigma, MessageComparator *comparator);

  void crackMessage();
  void getResult(uint *rotorPositions);

private:
  void setMessageToDecode(uint *message, uint messageLength) override;
  void setSampleToFind(uint *expected, uint expectedLength) override;
  void RecvMessage();
  void RecvExpectedMsg();
  double CalculateSingleOperation();
  void MeasureTime();
  void AssignWorkForProcesses(const double *const times);
  void RecvWorkFromRoot();
  std::array<uint, MAX_ROTORS> ConvertPositionToRotors(uint64_t number);
  void CheckForSolution(bool currentProcessStatus,
                        std::array<uint, MAX_ROTORS> &r);

private:
  // TO DO: Add dynamic DIVISION_N

  uint NUMBER_OF_ROTORS_VALUES;
  uint MAX_ROTORS_VALUE;
  uint64_t COMBINATIONS_NUMBER;
  std::size_t DIVISIONS_N;
  uint operationsNumberForTest;
  bool foundGlobalSolution{false};

  int processRank;
  int totalNumberOfProcesses;
  std::vector<uint64_t> startPosition, endPosition;

  virtual ~MPIEnigmaBreaker();
};